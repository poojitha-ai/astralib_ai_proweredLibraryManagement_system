# LEDGERMIND: Provenance-Constrained Multimodal Agentic Reasoning with a Structured Evidence Ledger

**Authors:** Enjun Du, Hange Zhou, Chenxu Du, Siyi Liu, Zirong Chen, Ziyu Zheng, Yongqi Zhang  
**Publication Date:** 2026-07-30  
**Categories:** cs.LG  
**Document ID:** DOC-ARXIV-2607.28374v1  

## Abstract
Multimodal agents for visual question answering increasingly operate as multi-step trajectories that interleave perception, retrieval, and reasoning, yet evaluation still largely reduces to final-answer accuracy. This aggregate signal cannot tell whether a correct answer was reached through grounded evidence, language priors, or accidental error cancellation. We propose to treat a multimodal agent trajectory as a provenance-constrained state machine: tool outputs are normalized into a Structured Evidence Ledger that serves as the trajectory state, downstream reasoning and decision claims may cite only active ledger entries, grounding is checked at the entity and numeric level, and repair is realized as typed state transitions that cannot introduce content without tool-produced provenance. We instantiate this design as LedgerMind (Provenance-Constrained Multimodal Agentic Reasoning with a Structured Evidence Ledger), augmented by a Three-Layer Grounding Protocol, an Adaptive Dual-Path Dispatcher that matches reasoning depth to question complexity, and an Event-Triggered Verification-and-Repair engine with a formal provenance non-amplification guarantee. We use LedgerMind to target four recurring failure patterns that final-answer accuracy tends to obscure: unsupported intermediate reasoning, citation-backed entity hallucination (Phantom Grounding), over-reasoning on simple queries, and repair-time amplification. Experiments across multiple multimodal reasoning benchmarks and backbone MLLMs show that LedgerMind improves both answer accuracy and trajectory-level faithfulness.

## 1. Introduction
Recent advancements in cs.LG have significantly transformed knowledge discovery systems. This document explores ledgermind: provenance-constrained multimodal agentic reasoning with a structured evidence ledger and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Enjun Du, Hange Zhou, Chenxu Du, Siyi Liu, Zirong Chen, Ziyu Zheng, Yongqi Zhang established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, LEDGERMIND: Provenance-Constrained Multimodal Agentic Reasoning with a Structured Evidence Ledger provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

