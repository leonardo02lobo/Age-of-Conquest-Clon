const fs=require('fs');
const md=fs.readFileSync(process.argv[2],'utf8');
const blocks=[...md.matchAll(/```mermaid\r?\n([\s\S]*?)```/g)].map(m=>m[1]);
console.log('bloques mermaid encontrados:',blocks.length);
blocks.forEach((b,i)=>fs.writeFileSync(`diag${i}.mmd`,b));
