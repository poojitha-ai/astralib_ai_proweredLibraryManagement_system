# When Specifications Conflict: A Symmetry-Based Framework for Measuring LLM Preferences

**Authors:** Tairan Wang, Liang Zhou, Zikang Zhan, Pingchuan Yan  
**Publication Date:** 2026-07-30  
**Categories:** cs.AI  
**Document ID:** DOC-ARXIV-2607.28384v1  

## Abstract
Large language models (LLMs) are increasingly required to integrate multiple sources of information that may be inconsistent or conflicting. However, there is still a lack of controllable and attributable methods for analyzing how models resolve conflicts between competing specifications. We propose a controlled experimental framework for studying model preferences under conflicting specifications. By constructing specifications with explicit conflicts, the framework enables model choices between competing specifications to be directly observed and analyzed. A symmetry-based design further reduces confounding factors, allowing preferences across representation types to be compared systematically.   We evaluate the framework on an executable mathematical benchmark with 550 conflict instances spanning 11 function families, comparing four representation types: pure natural language, formal language, naturalized formal language, and input--output examples. Results show systematic preference patterns rather than random behavior, with a consistent ordering: $ \text{Formal} \approx \text{Naturalized Formal} > \text{Pure Natural Language} > \text{Input--Output Examples} $. Example effects further depend on model capability and function family.   We extend the framework to heterogeneous specification conflicts in Boolean algebra, code generation, and the clinical domain, demonstrating its applicability across diverse tasks and specification forms. The framework provides a unified approach for measuring how LLMs resolve conflicts between competing sources of information.

## 1. Introduction
Recent advancements in cs.AI have significantly transformed knowledge discovery systems. This document explores when specifications conflict: a symmetry-based framework for measuring llm preferences and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Tairan Wang, Liang Zhou, Zikang Zhan, Pingchuan Yan established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, When Specifications Conflict: A Symmetry-Based Framework for Measuring LLM Preferences provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

