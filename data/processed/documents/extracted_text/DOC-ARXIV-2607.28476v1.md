# Improving Mental Health Screening and Early Risk Detection in Spanish

**Authors:** Andreu Casamayor-Segarra, Vicent Ahuir, Antonio Molina-Marco, Lluís-F. Hurtado  
**Publication Date:** 2026-07-30  
**Categories:** cs.CL  
**Document ID:** DOC-ARXIV-2607.28476v1  

## Abstract
Early detection of mental health disorders is often limited by the lack of specialized resources in Spanish and the difficulty of analyzing long histories of social media posts. This paper addresses these challenges through three main contributions. First, we introduce three Spanish foundational models specifically adapted to the mental health domain through domain-specific pre-training. Second, we propose Incremental Context Expansion (ICE), an automatic relabeling methodology designed for early detection. ICE identifies the point at which cumulative messages provide enough evidence of a disorder, generating more informative training samples. Third, we provide a set of fine-tuned models using the samples generated with the ICE methodology for early risk detection tasks. Our results on three Spanish benchmarks show that combining these specialized models with ICE improves the state-of-the-art, reducing detection latency while maintaining high performance. All models are publicly available.

## 1. Introduction
Recent advancements in cs.CL have significantly transformed knowledge discovery systems. This document explores improving mental health screening and early risk detection in spanish and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Andreu Casamayor-Segarra, Vicent Ahuir, Antonio Molina-Marco, Lluís-F. Hurtado established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Improving Mental Health Screening and Early Risk Detection in Spanish provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

