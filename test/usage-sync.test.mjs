import test from 'node:test';
import assert from 'node:assert/strict';
import {MobileSync} from '../src/vendor/mobile-sync.js';
test('quota-only changes update metadata without retransmitting transcript',()=>{
 const sync=new MobileSync();
 const source={session:{id:'fixture'},thread:{turns:[{id:'t',status:'completed',items:[{id:'m',type:'agentMessage',text:'Synthetic text'}]}]},
   observation:{state:'idle'},pendingQuestions:[],pendingApprovals:[],usage:{status:'snapshot',limits:[{id:'codex',capturedAt:1000,windows:[{usedPercent:80}]}]}};
 const first=sync.sync('fixture',source);
 const unchanged=sync.sync('fixture',source,first.cursor);assert.equal(unchanged.unchanged,true);
 source.usage.limits[0].capturedAt=2000;source.usage.limits[0].windows[0].usedPercent=89;
 const next=sync.sync('fixture',source,first.cursor);
 assert.notEqual(next.cursor,first.cursor);assert.equal(next.usage.limits[0].windows[0].usedPercent,89);
 assert.deepEqual(next.items,[]);
});
