/**
 * مزامنة أرشيف شركات (النسخة 2 — أسرع وأأمن) — مكتب محمود سالم للمحاسبة والمراجعة وتأسيس الشركات
 * ---------------------------------------------------------------
 * الصقي الكود ده مكان القديم في Apps Script، وبعدين: نشر ▸ إدارة عمليات النشر ▸ تعديل ▸ إصدار جديد ▸ نشر
 * (نفس الرابط بيفضل شغال — مش محتاجة تغيّري حاجة في الأجهزة).
 *
 * الجديد:
 *  - بيرجّع بس السجلات اللي اتغيرت من آخر مزامنة (بدل الأرشيف كله كل مرة).
 *  - بيحدّث الصفوف المتغيرة بس (بدل مسح الشيت وإعادة كتابته).
 *  - قفل (LockService) علشان لو جهازين زامنوا في نفس اللحظة ما يضيعش شغل.
 */

var TOKEN = 'مكتب-محمود-سالم-2026';

var SHEET_NAME = 'companies';
var HEAD = ['id','nameAr','client','updatedAt','json'];

function _sheet(){
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sh = ss.getSheetByName(SHEET_NAME);
  if(!sh){ sh = ss.insertSheet(SHEET_NAME); sh.appendRow(HEAD); }
  return sh;
}
function _out(obj){
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
function _ok(tok){ return !TOKEN || tok===TOKEN; }
function _ms(v){ var t=new Date(v||0).getTime(); return isNaN(t)?0:t; }

// قراءة: ?since=<ms> يرجّع بس اللي اتغير بعد الوقت ده
function doGet(e){
  var p=(e && e.parameter) || {};
  if(!_ok(p.token)) return _out({error:'unauthorized'});
  var since=+p.since||0, now=Date.now();
  var sh=_sheet(), n=sh.getLastRow(), out=[];
  if(n>1){
    var vals=sh.getRange(2,1,n-1,5).getValues();
    for(var i=0;i<vals.length;i++){
      var j=vals[i][4]; if(!j) continue;
      if(since && _ms(vals[i][3])<=since) continue;   // لم يتغير — ما نفتحوش
      try{ out.push(JSON.parse(j)); }catch(err){}
    }
  }
  return _out({companies:out, serverTime:now, partial:!!since});
}

// كتابة: بيدمج (الأحدث يكسب) ويحدّث الصفوف المتغيرة فقط
function doPost(e){
  var body={};
  try{ body=JSON.parse((e.postData && e.postData.contents) || '{}'); }catch(err){}
  if(!_ok(body.token)) return _out({error:'unauthorized'});
  var incoming=body.companies||[];
  var lock=LockService.getScriptLock();
  try{ lock.waitLock(25000); }catch(err){ return _out({error:'busy'}); }
  try{
    var sh=_sheet(), n=sh.getLastRow(), rowOf={}, tmOf={};
    if(n>1){
      var ids=sh.getRange(2,1,n-1,4).getValues();
      for(var i=0;i<ids.length;i++){ rowOf[ids[i][0]]=i+2; tmOf[ids[i][0]]=_ms(ids[i][3]); }
    }
    var adds=[];
    incoming.forEach(function(r){
      if(!r||!r.id) return;
      var t=_ms(r.updatedAt||r.createdAt);
      var row=[r.id,(r.state&&r.state.nameAr)||'',(r.state&&r.state.clientName)||'',r.updatedAt||r.createdAt||'',JSON.stringify(r)];
      if(rowOf[r.id]){
        if(t>=tmOf[r.id]) sh.getRange(rowOf[r.id],1,1,5).setValues([row]);
      } else { adds.push(row); rowOf[r.id]=-1; }
    });
    if(adds.length) sh.getRange(sh.getLastRow()+1,1,adds.length,5).setValues(adds);
    SpreadsheetApp.flush();
  } finally { lock.releaseLock(); }
  return _out({ok:true, serverTime:Date.now()});
}
