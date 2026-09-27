import fs from 'fs';

const f = 'shared/i18n/locales/en/chat.json';
let content = fs.readFileSync(f, 'utf8');

// Fix mojibake patterns
content = content.replace(/鈥\?,(\s*\n)/g, '...",$1');
content = content.replace(/鈥\?/g, '\u2014');
// 鈥淰 → \" (escaped inner quote)
content = content.replace(/鈥淰/g, '\\"');
// 鈥渰 → \" (escaped inner quote)  
content = content.replace(/鈥渰/g, '\\"');
// 鈥 before } → "
content = content.replace(/鈥(\s*})/g, '"$1');
// 鈥 before , → ",
content = content.replace(/鈥,/g, '",');
// Remaining 鈥 → "
content = content.replace(/鈥/g, '"');

try {
  const parsed = JSON.parse(content);
  fs.writeFileSync(f, JSON.stringify(parsed, null, 4) + '\n', 'utf8');
  console.log('Fixed!');
} catch (e) {
  console.log('Still broken:', e.message);
  const pos = parseInt(e.message.match(/position (\d+)/)?.[1] || '0');
  console.log('Around position', pos, ':', JSON.stringify(content.substring(pos - 40, pos + 40)));
}
