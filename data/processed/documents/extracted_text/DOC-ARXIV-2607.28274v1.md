# MORFES: A Benchmark for Productive Inflectional Competence in Modern Greek

**Authors:** Ioakeim Perros, Cleopatra Papadopoulou, Ayoub Kirouane, Christos Petrocheilos  
**Publication Date:** 2026-07-30  
**Categories:** cs.CL, cs.LG  
**Document ID:** DOC-ARXIV-2607.28274v1  

## Abstract
Modern Greek is a richly inflected language, yet the language models built for it are evaluated mainly on factual knowledge, and no benchmark is dedicated to their inflectional competence. We introduce MORFES (Morphological Open-class Recognition-and-Formation Evaluation Suite), a benchmark of 500 expert-verified items that tests the recognition and production of Greek inflected forms, favoring lower-frequency lemmas so that a correct answer reflects the rule rather than a memorized form. We make it publicly available at https://huggingface.co/datasets/KIEFERSA/MORFES. We evaluate a range of open language models on MORFES, situating them within the rapidly scaling open-weight ecosystem from LLaMA to Qwen3, DeepSeek-R1, Magistral, and Kimi K2, where multilingual coverage grows but grammatical competence in morphologically rich languages remains under-measured. Among them, Sophea-Genesis-1, a model we developed and release as open weights at https://huggingface.co/KIEFERSA/Sophea-Genesis-1, leads on inflectional morphology while matching similarly sized models in general capability.

## 1. Introduction
Recent advancements in cs.CL, cs.LG have significantly transformed knowledge discovery systems. This document explores morfes: a benchmark for productive inflectional competence in modern greek and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Ioakeim Perros, Cleopatra Papadopoulou, Ayoub Kirouane, Christos Petrocheilos established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, MORFES: A Benchmark for Productive Inflectional Competence in Modern Greek provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

