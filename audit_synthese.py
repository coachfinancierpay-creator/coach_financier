import json, statistics
from pathlib import Path
p=Path('data/synthese_financier.json'); d=json.loads(p.read_text(encoding='utf-8'))
months=d['monthlySummaries']; incomes=[m['income'] for m in months]; expenses=[m['expenses'] for m in months]; savings=[m['savings'] for m in months]
print('JSON OK')
print('months',len(months),'income',round(sum(incomes),2),'expenses',round(sum(expenses),2),'savings',round(sum(savings),2))
print('avg',round(statistics.mean(incomes),2),round(statistics.mean(expenses),2),round(statistics.mean(savings),2))
print('declared global',d.get('global'))
print('savings accounts',sum(x.get('solde',0) for x in d.get('epargne',[])),'declared',d.get('savingsTotal'))
for m in months:
    delta=round(m['income']-m['expenses']-m['savings'],2)
    cat=round(sum((m.get('categories') or {}).values())-m['expenses'],2)
    if delta or cat: print('incoherent',m['month'],'savings delta',delta,'categories delta',cat)
print('financialSummary present', 'financialSummary' in d)
