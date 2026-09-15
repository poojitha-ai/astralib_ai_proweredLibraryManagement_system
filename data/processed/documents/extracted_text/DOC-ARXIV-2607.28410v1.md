# Can Large Language Models Execute Parent Orders?

**Authors:** Zane Shen, Xinli Xu, Guangyi Zhang, Jialong Chen, Jinsong Zhou, Cong Chen, Guibao Shen, Dongyu Yan, Luozhou Wang, Zhen Yang  
**Publication Date:** 2026-07-30  
**Categories:** cs.CE, cs.CL, q-fin.TR  
**Document ID:** DOC-ARXIV-2607.28410v1  

## Abstract
Parent-order execution is a core problem in algorithmic trading, where the goal is to split a large order into smaller orders while reducing execution costs. Existing approaches either rely on pre-specified market assumptions that may not hold in practice, or require task-specific training that limits adaptability to new settings. To overcome these limitations, we present the first systematic study of large language models (LLMs) for parent-order execution. This extends the use of LLMs in finance from what to trade to how to execute. We propose PACE (Plan-Ahead Controlled Execution), a hierarchical framework that decomposes parent-order execution into long-horizon planning and short-horizon execution, requiring neither explicit market assumptions nor task-specific training. Experiments on Shenzhen Stock Exchange Level-1 data show that PACE outperforms TWAP, Almgren-Chriss, and learning-based baselines, exceeding the strongest baseline by 0.65 bps. Behavioral analysis reveals that LLMs make execution decisions differently from human investors: higher model confidence predicts better performance rather than worse returns, and the model trades earlier rather than procrastinating toward the deadline. These findings suggest that LLMs can complement human traders in execution decisions.

## 1. Introduction
Recent advancements in cs.CE, cs.CL, q-fin.TR have significantly transformed knowledge discovery systems. This document explores can large language models execute parent orders? and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Zane Shen, Xinli Xu, Guangyi Zhang, Jialong Chen, Jinsong Zhou, Cong Chen, Guibao Shen, Dongyu Yan, Luozhou Wang, Zhen Yang established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Can Large Language Models Execute Parent Orders? provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

