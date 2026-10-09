/**
 * Ảnh Định Vị – nhận dữ liệu từ ứng dụng và ghi vào Google Sheet.
 * Dán toàn bộ file này vào Tiện ích mở rộng > Apps Script của Google Sheet.
 */
const SHEET_NAME  = 'Nhật ký ảnh';
const FOLDER_NAME = 'Ảnh Định Vị';
const HEADERS = ['Thời điểm', 'Ngày', 'Giờ', 'Thứ', 'Người chụp', 'Địa chỉ',
                 'Vĩ độ', 'Kinh độ', 'Sai số (m)', 'Ghi chú', 'Bản đồ', 'Ảnh', 'Mã bản ghi'];

function doPost(e) {
  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    const d = JSON.parse(e.postData.contents);
    const sh = getSheet_();

    // Bỏ qua bản ghi đã có (khi app gửi lại do mất mạng)
    if (d.id && sh.getLastRow() > 1) {
      const ids = sh.getRange(2, HEADERS.length, sh.getLastRow() - 1, 1).getValues().flat();
      if (ids.indexOf(d.id) !== -1) return json_({ ok: true, duplicate: true });
    }

    let photoUrl = '';
    if (d.image) {
      const blob = Utilities.newBlob(Utilities.base64Decode(d.image), 'image/jpeg',
                                     d.fileName || ('ANH_' + Date.now() + '.jpg'));
      photoUrl = getFolder_().createFile(blob).getUrl();
    }

    const map = (d.lat != null) ? 'https://maps.google.com/?q=' + d.lat + ',' + d.lng : '';
    sh.appendRow([
      new Date(d.timestamp), d.date || '', d.time || '', d.weekday || '', d.user || '',
      d.address || '', d.lat, d.lng, d.accuracy, d.note || '', map, photoUrl, d.id || ''
    ]);
    return json_({ ok: true, photoUrl: photoUrl });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  } finally {
    lock.releaseLock();
  }
}

function doGet() {
  return json_({ ok: true, sheet: SpreadsheetApp.getActiveSpreadsheet().getName() });
}

function getSheet_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sh = ss.getSheetByName(SHEET_NAME);
  if (!sh) sh = ss.insertSheet(SHEET_NAME);
  if (sh.getLastRow() === 0) {
    sh.appendRow(HEADERS);
    sh.getRange(1, 1, 1, HEADERS.length).setFontWeight('bold').setBackground('#ffe9a8');
    sh.setFrozenRows(1);
    sh.getRange('A:A').setNumberFormat('dd/MM/yyyy HH:mm:ss');
  }
  return sh;
}

function getFolder_() {
  const it = DriveApp.getFoldersByName(FOLDER_NAME);
  return it.hasNext() ? it.next() : DriveApp.createFolder(FOLDER_NAME);
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

/** Chạy hàm này một lần trong trình soạn thảo để cấp quyền Sheet + Drive. */
function capQuyen() {
  getSheet_();
  getFolder_();
}
