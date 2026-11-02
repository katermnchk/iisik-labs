"""Run the actual Postman scripts with Newman, retaining a token-free summary."""
import os,pathlib,shutil,subprocess
root=pathlib.Path(__file__).resolve().parents[1]
bundled=pathlib.Path('/Users/katar/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node')
node=os.environ.get('NODE_BINARY') or (str(bundled) if bundled.exists() else shutil.which('node'))
module=os.environ.get('NEWMAN_MODULE',str(pathlib.Path('/Users/katar/outputs/iisik-labs567/newman/node_modules/newman')))
if not node or not pathlib.Path(module).exists():
 raise SystemExit('Newman is required. Install it into a temporary tools directory with npm install --prefix <tools> newman, then set NEWMAN_MODULE=<tools>/node_modules/newman (and NODE_BINARY if needed).')
js=r'''
const fs=require('fs');const newman=require(process.argv[1]);const root=process.argv[2];
const options={collection:JSON.parse(fs.readFileSync(root+'/examples/labs567.postman_collection.json')),reporters:['cli'],timeoutRequest:20000,timeoutScript:30000};
if(process.env.LAB_PASSWORD) options.envVar=[{key:'password',value:process.env.LAB_PASSWORD}];
newman.run(options,(error,summary)=>{
 if(error){console.error(error.message);process.exitCode=1;return;}
 const failures=summary.run.failures.map(f=>({source:f.source&&f.source.name,test:f.error&&f.error.test,message:f.error&&f.error.message}));
 const out={runner:'Newman',version:require(process.argv[1]+'/package.json').version,date:new Date().toISOString(),collectionSteps:options.collection.item.length,requests:summary.run.stats.requests,assertions:summary.run.stats.assertions,failures,elapsedMs:summary.run.timings.completed-summary.run.timings.started};
 fs.writeFileSync(root+'/docs/evidence/labs567/collection.json',JSON.stringify(out,null,2)+'\n');
 process.exitCode=failures.length?1:0;
});
'''
raise SystemExit(subprocess.run([node,'-e',js,module,str(root)],cwd=root).returncode)
