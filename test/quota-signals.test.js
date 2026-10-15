import test from 'node:test';
import assert from 'node:assert/strict';
import {mobileSignals, mobileReadMarker, MobileSync} from '../src/vendor/mobile-sync.js';
test('quota motion uses explicit native state, never an incomplete or failed historical turn',()=>{
 const source={thread:{turns:[{id:'turn',status:'failed',items:[]}]},session:{mode:'default'},pendingQuestions:[],pendingApprovals:[]};
 assert.equal(mobileSignals(source).running,null);
 source.observation={source:'desktop-ipc',state:'idle'};assert.equal(mobileSignals(source).running,false);
 source.observation.state='running';assert.equal(mobileSignals(source).running,true);
 source.pendingQuestions=[{id:'q',status:'pending'}];assert.equal(mobileSignals(source).running,false);
 source.pendingQuestions[0].status='closed';assert.equal(mobileSignals(source).running,true);
 source.pendingApprovals=[{id:'a'}];assert.equal(mobileSignals(source).running,false);
});

test('read revision matches sync and changes for edited final output or question revision',()=>{
 const source={thread:{turns:[{id:'t',status:'completed',items:[{id:'a',type:'agentMessage',text:'result'}]}]},session:{mode:'default'},observation:{source:'desktop-ipc',state:'idle'},pendingQuestions:[],pendingApprovals:[]};
 const before=mobileSignals(source),sync=new MobileSync().sync('thread',source);
 assert.match(before.readRevision,/^[a-f0-9]{64}$/);assert.equal(before.readRevision,sync.readRevision);
 assert.equal(before.readKey,sync.items.at(-1).key);
 source.thread.turns[0].items[0].text='edited';assert.notEqual(mobileSignals(source).readRevision,before.readRevision);
 source.pendingQuestions=[{id:'q',revision:'v1',status:'pending'}];const q=mobileSignals(source);
 assert.equal(q.readKey,'question-q-v1');source.pendingQuestions[0].revision='v2';assert.notEqual(mobileSignals(source).readRevision,q.readRevision);
 source.thread.turns[0].status='inProgress';source.pendingQuestions=[];assert.equal(mobileReadMarker(source).readRevision,undefined);
});
