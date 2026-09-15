# From Textual Requirements to Microservice Architectures - A Comprehensive Evaluation of LLM-Based Design Synthesis

**Authors:** Danyllo Albuquerque, José Renan, Guillermo Rodríguez, Guillermo Rodríguez, Emanuel Dantas, Ademar França, Mirko Perkusich, Kyller Gorgônio, Angelo Perkusich  
**Publication Date:** 2026-07-30  
**Categories:** cs.SE, cs.AI  
**Document ID:** DOC-ARXIV-2607.28307v1  

## Abstract
Microservice architectures have become dominant for modernizing monolithic systems, yet identifying appropriate services remains challenging and largely manual. Existing decomposition approaches are predominantly code-centric, limiting applicability in early design stages where only textual requirements are available. Despite advances in Large Language Models (LLMs), limited empirical evidence exists on their ability to synthesize complete microservice architectures from natural-language requirements, including service definitions and inter-service interactions. This study investigates whether an LLM can bridge requirements engineering and architectural design, generating architectures solely from textual requirements and evaluating structural agreement and perceived quality of results. We conduct a mixed-method study using OpenAI o3 under zero-shot (ZS) and few-shot (FS) prompting across two systems (Bookstore, PetClinic), one execution per system/condition. Architectures are evaluated through (i) comparison with reference architectures using precision, recall, and F1-score for service identification and communication recovery, and (ii) a blinded expert assessment of correctness, completeness, modularity, and plausibility, plus open feedback synthesis. OpenAI o3 identifies services with higher agreement under FS prompting (F1 = 0.79 for ZS versus = 0.97 for FS). Communication recovery is more challenging: ZS produces dense architectures with high recall but low precision (F1 = 0.61), while FS improves agreement, reaching F1 = 0.82 and reducing unsupported dependencies. Expert evaluation corroborates these results, with FS architectures perceived as more modular, coherent, and plausible than ZS outputs. OpenAI o3 shows potential for requirements-driven synthesis when guided by exemplar prompting. Results are model- and context-specific from two small systems, not model-independent proof.

## 1. Introduction
Recent advancements in cs.SE, cs.AI have significantly transformed knowledge discovery systems. This document explores from textual requirements to microservice architectures - a comprehensive evaluation of llm-based design synthesis and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Danyllo Albuquerque, José Renan, Guillermo Rodríguez, Guillermo Rodríguez, Emanuel Dantas, Ademar França, Mirko Perkusich, Kyller Gorgônio, Angelo Perkusich established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, From Textual Requirements to Microservice Architectures - A Comprehensive Evaluation of LLM-Based Design Synthesis provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

