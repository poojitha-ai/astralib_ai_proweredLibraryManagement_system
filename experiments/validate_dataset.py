import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
P = ROOT / 'data' / 'processed' / 'documents' / 'documents_full_rag.json'
D = json.loads(P.read_text(encoding='utf-8'))
chunks = [c for d in D for c in d.get('chunks', [])]
print('documents =', len(D))
print('chunks =', len(chunks))
print('chunk counts =', Counter(len(d.get('chunks', [])) for d in D))
print('unique document ids =', len({d['document_id'] for d in D}))
print('unique chunk ids =', len({c['chunk_id'] for c in chunks}))
print('missing titles =', sum(not d.get('title') for d in D))
print('missing chunk text =', sum(not c.get('content', c.get('text')) for c in chunks))
print('sections =', sorted({c.get('section') for c in chunks if c.get('section')}))
