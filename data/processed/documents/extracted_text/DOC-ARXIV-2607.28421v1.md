# When Derived Measurements Mislead: Quantifying and Mitigating LLM Over-Trust with Privileged-Modality Reliability Evidence

**Authors:** Zongheng Guo, Tao Chen, Tianli Li, Mingzhe Cui, Yang Jiao, Lei Xie, Yi Pan, Xiao Hu, Manuela Ferrario  
**Publication Date:** 2026-07-30  
**Categories:** cs.AI  
**Document ID:** DOC-ARXIV-2607.28421v1  

## Abstract
Derived measurements increasingly enter large language model (LLM) pipelines as direct facts despite their instance-dependent validity. We define derived-feature over-trust (DFOT) as the failure in which a downstream LLM assigns such a measurement the epistemic status of a direct fact or uses it outside its valid scope. Using physiological sensing as a case study, D1 tests acceptance of a PPG-derived rhythm contradicted by offline ECG, whereas D2 tests rejection of an offline-confirmed reliable PPG rhythm under misleading severe history. ECG supplies training supervision and offline reference construction but is never shown to the LLM. Five estimands quantify this chain: conflict over-trust rate (COTR) and context-induced error rate (CIR) characterize D1/D2; correct repair rate (CRR) measures frozen-error repair; evidence-specific repair margin (ESRM) contrasts matched and patient-disjoint shuffled evidence; and utility harm rate (UHR) measures unnecessary verification among HIGH-reliability cases used without verification at baseline. The framework does not depend on a particular reliability generator. We demonstrate it on 50,000 paired PPG-ECG records using ECG-to-PPG privileged distillation as an illustrative baseline and PPG-only inference. On a protocol-locked 187-patient test, the baseline improves four repair and specificity endpoints by 1.82-6.69 percentage points, with all paired confidence intervals excluding zero; UHR increases by 0.67 percentage points (95% CI: -0.4 to +1.7). DFOT provides a common evaluation target for stronger mitigation methods. The code is available at https://github.com/Zongheng-Guo/When-Derived-Measurements-Mislead.

## 1. Introduction
Recent advancements in cs.AI have significantly transformed knowledge discovery systems. This document explores when derived measurements mislead: quantifying and mitigating llm over-trust with privileged-modality reliability evidence and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Zongheng Guo, Tao Chen, Tianli Li, Mingzhe Cui, Yang Jiao, Lei Xie, Yi Pan, Xiao Hu, Manuela Ferrario established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, When Derived Measurements Mislead: Quantifying and Mitigating LLM Over-Trust with Privileged-Modality Reliability Evidence provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

