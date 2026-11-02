from pathlib import Path
import secrets
p=Path(__file__).resolve().parents[1]/'.env'
if not p.exists():
 p.write_text('SERVICE_KEY='+secrets.token_hex(32)+'\nJWT_SECRET='+secrets.token_hex(32)+'\nLAB_PASSWORD=Lab567-Travel!\n')
print('Local environment ready')
