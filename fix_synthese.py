import json
from pathlib import Path
s=Path('data/synthese_financier.json'); d=json.loads(s.read_text(encoding='utf-8'))
actual=round(sum(float(x.get('solde') or 0) for x in d.get('epargne', [])),2)
d['savingsTotal']=actual
d.setdefault('global',{})['savingsTotal']=actual
d['global']['savingsBalance']=actual
d.setdefault('financialSummary',{})['savingsBalance']=actual
bank=Path('data/banking_demo_normalized.json')
if bank.exists():
    b=json.loads(bank.read_text(encoding='utf-8'))
    count=sum(len(m.get('operations',[])) for account in b.get('accounts',[]) for m in account.get('transactions',{}).get('months',[]))
    d['financialSummary']['transactionCount']=count
    d['global']['transactionCount']=count
s.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('savingsTotal=',actual,'transactionCount=',d['financialSummary'].get('transactionCount'))
