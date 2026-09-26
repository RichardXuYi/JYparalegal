import fs from 'fs';

const files = ['src/stores/chat/history-actions.ts', 'src/stores/chat/session-actions.ts'];

for (const f of files) {
  console.log(`\n=== ${f} ===`);
  const c = fs.readFileSync(f, 'utf8');
  const lines = c.split('\n');
  
  // Find all backticks
  let inTemplate = false;
  let templateStartLine = 0;
  
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    for (let j = 0; j < line.length; j++) {
      if (line[j] === '`' && (j === 0 || line[j - 1] !== '\\')) {
        inTemplate = !inTemplate;
        if (inTemplate) {
          templateStartLine = i + 1;
          console.log(`Template starts at line ${i + 1}: ${line.substring(Math.max(0, j - 20), j + 30)}`);
        } else {
          console.log(`Template ends at line ${i + 1}: ${line.substring(Math.max(0, j - 20), j + 30)}`);
        }
      }
    }
  }
  
  if (inTemplate) {
    console.log(`ERROR: Unterminated template literal starting at line ${templateStartLine}`);
  }
}
