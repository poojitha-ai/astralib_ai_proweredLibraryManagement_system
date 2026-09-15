# Beyond a Single Judge: Simulating Social Persona Panels for Generative UI Evaluation

**Authors:** Zheng Wu, Yibo Luo, Pu Zhang, Cheng Yang, Zhuosheng Zhang  
**Publication Date:** 2026-07-30  
**Categories:** cs.CL  
**Document ID:** DOC-ARXIV-2607.28439v1  

## Abstract
Generative UI (GenUI) lets large language models synthesize a complete, renderable interface directly from a natural-language instruction, but evaluating the quality of what they generate remains an open problem. Human evaluation is costly and rater-variant, while LLM-as-a-judge is scalable but reflects only a single implicit viewpoint, unable to capture how different populations of real users actually perceive the same interface. We propose the Evidence-Grounded, Social-Weighted Persona Panel (ESPP), a three-stage GenUI evaluation method in which a panel of psychologically diverse, evidence-grounded personas independently rates a screenshot, exchanges opinions under a trait-derived, semantically-gated bounded-confidence mechanism, and is aggregated via Delphi-inspired social weighting into a single judgment. ESPP tracks human judgment substantially more closely than a naive single-pass judge, raising Pearson $r$ from $0.716$ to $0.922$, and a prompt-ensemble control recovers only about a third of this gap, isolating genuine persona and evidence grounding as the dominant source of improvement. Beyond this fidelity gain, retaining each panelist's individual rating further reveals that user subgroups agree on overall model rankings yet diverge sharply on specific rating dimensions, a structural disagreement a single homogeneous judge would systematically erase. The codes are available at https://github.com/Wuzheng02/ESPP.

## 1. Introduction
Recent advancements in cs.CL have significantly transformed knowledge discovery systems. This document explores beyond a single judge: simulating social persona panels for generative ui evaluation and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Zheng Wu, Yibo Luo, Pu Zhang, Cheng Yang, Zhuosheng Zhang established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Beyond a Single Judge: Simulating Social Persona Panels for Generative UI Evaluation provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

