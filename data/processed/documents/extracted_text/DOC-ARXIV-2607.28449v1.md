# Lightning OPD 2.0: Mitigating Style Bias in Cross-Teacher On-Policy Distillation for Large Reasoning Models

**Authors:** Yecheng Wu, Song Han, Han Cai  
**Publication Date:** 2026-07-30  
**Categories:** cs.CL  
**Document ID:** DOC-ARXIV-2607.28449v1  

## Abstract
On-policy distillation (OPD) provides dense token-level supervision from a teacher, but its effectiveness can depend on teacher consistency, meaning that the model providing OPD supervision should also have generated the demonstrations used to train the supervised fine-tuning (SFT) reference. However, this condition is frequently violated in practice when SFT data have mixed or unknown provenance or when different models are preferred for SFT data generation and subsequent distillation. In such cross-teacher settings, even a stronger OPD teacher can yield little improvement over the SFT reference. We find that raw teacher--reference disagreement contains potentially useful context-specific teacher evidence as well as a recurring component associated with differences in wording, formatting, and reasoning cadence. We introduce Lightning OPD 2.0 with cross-fitted style residualization, which uses rollout-level cross-fitting to estimate this recurring component as an operational proxy for style-token bias and subtracts it before constructing the token-level OPD update. Across mathematical reasoning and code generation benchmarks, Lightning OPD 2.0 consistently outperforms Lightning OPD in cross-teacher settings. Starting from Klear-Reasoner-8B-SFT, Lightning OPD 2.0 reaches 82.4% on AIME 2024 and 63.0% on LiveCodeBench v5. Together, these results establish Lightning OPD 2.0 as a practical approach to cross-teacher OPD, relaxing teacher consistency as a prerequisite and allowing the SFT data generator and distillation teacher to be selected independently. Code will be released soon.

## 1. Introduction
Recent advancements in cs.CL have significantly transformed knowledge discovery systems. This document explores lightning opd 2.0: mitigating style bias in cross-teacher on-policy distillation for large reasoning models and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Yecheng Wu, Song Han, Han Cai established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Lightning OPD 2.0: Mitigating Style Bias in Cross-Teacher On-Policy Distillation for Large Reasoning Models provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

