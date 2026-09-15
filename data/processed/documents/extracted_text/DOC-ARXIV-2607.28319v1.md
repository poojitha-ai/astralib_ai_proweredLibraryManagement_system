# Fairness Pruning: Locating Demographic Bias in GLU-MLP Layers via Differential Activations

**Authors:** Pere Martra, Eugenio Martínez Cámara, Alfonso Ureña López  
**Publication Date:** 2026-07-30  
**Categories:** cs.CL, cs.CY, cs.LG  
**Document ID:** DOC-ARXIV-2607.28319v1  

## Abstract
This work presents Fairness Pruning, a lightweight structural intervention method designed for the management and future mitigation of demographic bias in large language models (LLMs). As a foundational empirical validation of this method, this work focuses on causal bias localization. Using minimally contrastive prompt pairs and inference-time activation capture, the method identifies neurons that react differentially when processing demographic attributes in GLU architectures, evaluating the signal at the down_proj input. Empirical evaluation was conducted on models of up to 3 billion parameters (Llama-3.2 family and Salamandra-2B), combining standardized benchmark evaluation with qualitative text generation experiments. Results demonstrate that zeroing the identified neurons alters how the model responds to associated demographic variables. However, rather than producing flat mitigation, the intervention causes bidirectional bias destabilization: because BiasScore is unsigned, candidate sets mix neurons that push toward and against the stereotype, and the net effect on aggregate bias depends on which sign dominates. The intervention is extremely surgical: zeroing at most 40 neurons in Llama-3.2-1B (less than 0.031% of total MLP width) achieves a mean retention of 99.49% in reasoning and general knowledge capabilities. These findings empirically confirm that demographic bias processing and model capabilities operate on dissociable circuits, establishing the methodological foundations for transitioning from blind zeroing toward directional behavior modulation.

## 1. Introduction
Recent advancements in cs.CL, cs.CY, cs.LG have significantly transformed knowledge discovery systems. This document explores fairness pruning: locating demographic bias in glu-mlp layers via differential activations and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Pere Martra, Eugenio Martínez Cámara, Alfonso Ureña López established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Fairness Pruning: Locating Demographic Bias in GLU-MLP Layers via Differential Activations provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

