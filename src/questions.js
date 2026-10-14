// 把原生问题变成手机卡片。这里不创建任务、不批准权限；答案只交回原问题。
import {createHash} from 'node:crypto';
import {object, ControlError} from './vendor/desktop-ipc.js';
import {nativeTurns, activeTurn} from './vendor/desktop-state.js';

const hash = value => createHash('sha256').update(JSON.stringify(value)).digest('hex');
const text = (value, max=8000) => typeof value === 'string' && value.length <= max ? value : null;
const safeId = value => typeof value === 'string' && value.length > 0 && value.length <= 1000;
export const QUESTION_NOTICE = '这项操作需要在电脑处理；手机仍可阅读对话。';

function normalize(raw, kind) {
  if (!Array.isArray(raw) || !raw.length || raw.length > 3) return null;
  const seen = new Set(), result = [];
  for (const [index,q] of raw.entries()) {
    if (!object(q)) return null;
    const id = kind === 'async' ? String(index) : q.id;
    const question = kind === 'async' ? text(q.title) : text(q.question);
    if (!safeId(id) || seen.has(id) || !question?.trim()) return null;
    seen.add(id);
    const options = q.options ?? [];
    if (!Array.isArray(options) || options.length > 20) return null;
    const parsed = [], labels = new Set();
    for (const option of options) {
      const label = kind === 'async' ? text(option,2000) : text(option?.label,2000);
      const description = kind === 'async' ? '' : text(option?.description ?? '',4000);
      if (!label?.trim() || description === null || labels.has(label)) return null;
      labels.add(label); parsed.push({label,description});
    }
    result.push({id,header:text(q.header ?? '',1000) ?? '',question,options:parsed,
      isSecret:q.isSecret === true,isOther:kind === 'async' || q.isOther === true || !parsed.length});
  }
  return result;
}

function group(threadId,turnId,kind,nativeId,questions,status,notice='') {
  const id=hash([threadId,turnId,kind,nativeId]);
  return {id,revision:hash([id,questions]),turnId,kind,status,notice,questions,nativeId};
}

// 只识别 Desktop 实际接受的结构化异步回答。普通消息中的“同意”不是答题回执。
function acceptedAsyncIds(turns) {
  const ids=new Set();
  for(const turn of turns) for(const item of turn.items) {
    if(item.type !== 'steeringUserMessage' || item.status !== 'accepted') continue;
    const parts=item.type==='steeringUserMessage'?item.input:item.content;
    const input = typeof item.text === 'string' ? item.text : Array.isArray(parts)
      ? parts.filter(p=>p?.type==='text').map(p=>p.text).join('\n') : '';
    const m=/^<send_user_message_question_reply>\s*([\s\S]*?)\s*<\/send_user_message_question_reply>$/.exec(input.trim());
    if(!m) continue;
    try { const replies=JSON.parse(m[1]); if(Array.isArray(replies)) for(const r of replies)
      if(safeId(r?.questionItemId) && typeof r.answer==='string') ids.add(r.questionItemId);
    } catch { /* 未确认或不符合原生格式的文本不作为答题证据。 */ }
  }
  return ids;
}

export function nativeQuestions(state,{enabled=false}={}) {
  const turns=nativeTurns(state), active=activeTurn(state), result=[];
  let requiresDesktop=false;
  const requests=Array.isArray(state.requests)?state.requests:[];
  for(const r of requests) {
    const p=r?.params;
    if(r?.method!=='item/tool/requestUserInput' || !object(p)
       || p.threadId !== state.id || p.turnId !== active
       || !(safeId(r.id) || Number.isSafeInteger(r.id))) {requiresDesktop=true;continue;}
    const questions=normalize(p.questions,'user-input');
    if(!questions) {requiresDesktop=true;continue;}
    const supported=enabled && !questions.some(q=>q.isSecret);
    result.push(group(state.id,p.turnId,'user-input',r.id,questions,
      supported?'pending':'desktop_required',supported?'':QUESTION_NOTICE));
    requiresDesktop ||= !supported;
  }
  // 手机操作面板只展示最近一回合的问题；历史正文仍按原分页读取。
  const latest=turns.at(-1);
  const accepted=acceptedAsyncIds(latest?[latest]:[]);
  for(const item of latest?.items ?? []) {
    if(item?.type==='userInputResponse' && item.completed===true && item.turnId===latest.id
      && (safeId(item.requestId)||Number.isSafeInteger(item.requestId))) {
      const questions=normalize(item.questions,'user-input');
      if(questions && !result.some(q=>q.kind==='user-input' && q.nativeId===item.requestId))
        result.push(group(state.id,latest.id,'user-input',item.requestId,questions,'closed','已在电脑或其他设备处理。'));
    }
    if(item?.type!=='agentMessage' || !Array.isArray(item.questions) || !item.questions.length) continue;
    const normalized=normalize(item.questions,'async');
    if(!normalized || !safeId(item.id)) {requiresDesktop=true;continue;}
    for(const question of normalized) {
      const nativeId=JSON.stringify(['request_user_input_async',item.id,Number(question.id)]);
      const answered=accepted.has(nativeId);
      const ended=latest.id !== active;
      // 当前固定版本的 follower steer 不接受 expectedTurnId，不能原子锁定提问回合。
      // 先如实显示异步卡片；不使用 start-turn 或可能投向下一回合的 steer 代替回答。
      const supported=false;
      result.push(group(state.id,latest.id,'async',nativeId,[question],
        answered?'closed':ended?'closed':supported?'pending':'desktop_required',
        answered?'已在电脑或其他设备回答。':ended?'这轮对话已结束，问题不再等待回答。':
          '这是异步提问。当前 Desktop 的远程接口无法锁定提问回合，请在电脑选择；手机会同步处理状态。'));
      requiresDesktop ||= !answered && !ended && !supported;
    }
  }
  // 异常体量不裁切题目后继续提交；回退到明确的电脑处理提示。
  if(result.length>12 || Buffer.byteLength(JSON.stringify(result))>48*1024) return {groups:[],requiresDesktop:true};
  const ids=new Set();
  for(const q of result) {if(ids.has(q.id)) return {groups:[],requiresDesktop:true};ids.add(q.id);}
  return {groups:result,requiresDesktop};
}

export function validateAnswer(question,body) {
  if(!object(body) || Object.keys(body).some(k=>!['revision','answers'].includes(k))
    || body.revision!==question.revision || !object(body.answers)
    || Object.keys(body.answers).length!==question.questions.length)
    throw new ControlError('question_changed','问题已变化，请刷新核对后回答。');
  const answers=Object.create(null);
  for(const q of question.questions) {
    const value=body.answers[q.id];
    if(!Object.hasOwn(body.answers,q.id) || typeof value!=='string' || !value.trim() || value.length>8000
      || q.isSecret || (!q.isOther && q.options.length && !q.options.some(o=>o.label===value)))
      throw new ControlError('invalid_answer','请完整回答当前问题；未发送答案。',400);
    answers[q.id]={answers:[value]};
  }
  return {answers};
}

export function publicQuestion(q) { const {nativeId,...publicFields}=q; return publicFields; }

// Desktop 自己记录的 completed 回答项才是确认依据；RPC ok 或问题消失均不够。
export function answerEvidence(state,record) {
  const q=record.question;
  const item=nativeTurns(state).find(t=>t.id===q.turnId)?.items.find(i=>
    i.type==='userInputResponse' && i.requestId===q.nativeId && i.turnId===q.turnId && i.completed===true);
  if(!item || !object(item.answers) || !Array.isArray(item.questions)
    || questionProof(item.questions)!==q.questionProof) return null;
  return answerProof(item.answers)===q.answerProof ? 'accepted' : 'closed';
}
export const answerProof = answers => hash(Object.entries(answers).sort(([a],[b])=>a.localeCompare(b)));
export const questionProof = questions => hash(questions.map(q=>({id:q.id,header:q.header??'',question:q.question,
  options:(q.options??[]).map(o=>({label:o.label,description:o.description??''}))})));
