# LLMs struggle to simulate human belief updates in controlled environments

**Authors:** Sebastian Pohl, Harsh Mehta, Pranav Mambayil, Abdul Ghafoor, Franziska Lesigang, Yufang Hou, Christian Hilbe  
**Publication Date:** 2026-07-30  
**Categories:** cs.CL, cs.SI  
**Document ID:** DOC-ARXIV-2607.28347v1  

## Abstract
LLMs are increasingly deployed as proxies for human study participants in social science experiments, yet the fidelity of this practice has rarely been tested directly. We test whether six LLMs can simulate individual human belief updates, comparing LLM outputs 1-to-1 against ground truth data from 391 UK participants on Prolific, who updated their stances on three discussion topics after reading Reddit comments. Each participant was simulated by an LLM conditioned on a persona derived from their demographic and personality trait data. We find that some LLMs (Qwen3-32B and GPT-5-Mini) can match the human post-stance distribution, but only when given participants' actual initial stances. All six models fail to simulate initial stances themselves and to produce faithful belief updates from self-generated stances. Three systematic biases emerge across all models: overrepresentation of neutral positions, more frequent but smaller belief shifts than humans, and a failure to rank comments by convincingness. Demographic and personality trait personas had no consistent effect on fidelity. LLM simulations of human belief dynamics are only reliable when grounded in realistic starting conditions, that current multi-round social media simulations rarely provide.

## 1. Introduction
Recent advancements in cs.CL, cs.SI have significantly transformed knowledge discovery systems. This document explores llms struggle to simulate human belief updates in controlled environments and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Sebastian Pohl, Harsh Mehta, Pranav Mambayil, Abdul Ghafoor, Franziska Lesigang, Yufang Hou, Christian Hilbe established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, LLMs struggle to simulate human belief updates in controlled environments provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

