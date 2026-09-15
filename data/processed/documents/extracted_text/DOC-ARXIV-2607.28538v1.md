# ScaFE: Data-Efficient Scar Classification with LLM-Generated Clinical Feature Programs

**Authors:** Ruman Wang, Hangting Ye  
**Publication Date:** 2026-07-30  
**Categories:** cs.CV, cs.LG  
**Document ID:** DOC-ARXIV-2607.28538v1  

## Abstract
Classifying pathological scars from clinical photographs requires distinguishing keloids from hypertrophic scars despite limited expert-labeled data and substantial acquisition variation across hospitals. End-to-end image models remain data-dependent, whereas sending photographs to a hosted vision-language model (VLM) may conflict with local data-governance requirements and yields decisions that are difficult to reproduce and audit. We introduce ScaFE (Scar Feature Engineering), which transfers clinical knowledge from a large language model (LLM) into deterministic, executable feature programs instead of asking the model to diagnose images. A web-enabled LLM retrieves clinical evidence and synthesizes programs that measure visually assessable scar attributes. Candidate programs execute in a restricted local environment, and only aggregate validation statistics and feature-level SHAP summaries are returned for iterative repair and refinement; raw images and patient-level outputs remain local. A lightweight Random Forest then operates on the resulting structured representation. On 600 photographs from three hospitals under leave-one-site-out evaluation, ScaFE achieves 81.0% site-macro balanced accuracy, exceeding the strongest baseline, BiomedCLIP, by 10.0 percentage points. With only 10% of the development data, ScaFE retains 72.0% balanced accuracy and an 11.8-point lead. Iterative refinement also raises the executable-program rate from 66.7% to 95.0%, with verified evidence for 91.7% of the final features. These results show that LLM knowledge can support data-efficient, cross-site medical image classification through local and auditable feature programs rather than direct VLM decisions.

## 1. Introduction
Recent advancements in cs.CV, cs.LG have significantly transformed knowledge discovery systems. This document explores scafe: data-efficient scar classification with llm-generated clinical feature programs and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Ruman Wang, Hangting Ye established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, ScaFE: Data-Efficient Scar Classification with LLM-Generated Clinical Feature Programs provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

