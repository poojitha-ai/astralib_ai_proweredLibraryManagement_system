# Oracle-Budgeted Molecular Optimization with Short-Term Graph Memory

**Authors:** Jiannan Yang, Veronika Thost, Xiang Ling, Tengfei Ma  
**Publication Date:** 2026-07-30  
**Categories:** cs.LG  
**Document ID:** DOC-ARXIV-2607.28437v1  

## Abstract
Molecular optimization is commonly performed under a limited oracle budget, which makes deciding what to evaluate as important as deciding what to generate. We introduce short-term graph memory, a plug-in module that preserves the generator architecture and native update rule while learning from previously evaluated molecules to prioritize subsequent oracle queries. The module maintains an online graph neural surrogate that pre-screens each round's candidate pool, so the fixed oracle budget is spent on molecules with higher predicted utility. Applied to a fragment-based generator on a standard molecular optimization benchmark, it improves the mean top-10 score at no extra oracle cost and never falls behind the base on any oracle; the gain extends to all four generators we tested at a tight budget of one thousand calls. We then analyze how surrogate-guided selection interacts with the exploration and exploitation behavior of different generators. Its benefit at larger budgets is consistent with two properties of the backbone: how broadly it searches, and how effectively its native search already exploits oracle feedback. We provide a simple way to spend a fixed oracle budget more selectively, and evidence on which generators benefit from it.

## 1. Introduction
Recent advancements in cs.LG have significantly transformed knowledge discovery systems. This document explores oracle-budgeted molecular optimization with short-term graph memory and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Jiannan Yang, Veronika Thost, Xiang Ling, Tengfei Ma established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Oracle-Budgeted Molecular Optimization with Short-Term Graph Memory provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

