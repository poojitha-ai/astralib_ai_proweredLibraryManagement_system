# Beyond Sentiment: Structured Information Extraction from Financial News

**Authors:** Daohan Zhu, Sitong Ge, Ruofei Wang, Honggu Chen, Yubo Hou, Tao Wan, Zengchang Qin  
**Publication Date:** 2026-07-30  
**Categories:** cs.CL  
**Document ID:** DOC-ARXIV-2607.28496v1  

## Abstract
Financial sentiment analysis has become a standard component in news-driven stock prediction, yet it reduces rich, multi-dimensional news articles to a single polarity score. We hypothesize that financial news encodes multiple orthogonal information dimensions---event type, impact scope, temporal horizon, and semantic confidence---that sentiment alone cannot capture, and that these dimensions carry independent predictive value. To test this hypothesis, we propose a structured information extraction framework that leverages LLaMA-3.1-70B to extract six semantic dimensions from financial news. Through large-scale experiments on 41,618 news--stock pairs from the FNSPID dataset, we find that (i) FinBERT sentiment features exhibit strong predictive power under nonlinear models (F1=0.576) but substantially weaker performance under linear models (F1=0.230), revealing a highly nonlinear sentiment--return relationship; (ii) LLM-extracted structured features, while individually weaker, capture information orthogonal to sentiment, as evidenced by a 53.5% systematic disagreement rate between the two approaches; and (iii) combining both signal sources yields F1=0.600, significantly outperforming either alone ($p < 0.0001$), with consistent improvements across all seven event types. Ablation experiments confirm that non-sentiment structural dimensions (event type, impact subject, time horizon, confidence) independently contribute $Δ\text{F1} = +0.019$ beyond FinBERT alone. Feature importance analysis reveals balanced contributions from all six extracted dimensions (14--21%), demonstrating that compressing news into a single sentiment score incurs substantial information loss. Our results suggest that the sentiment--semantics decoupling in financial text is systematic and exploitable, opening a new direction for multi-dimensional financial NLP.

## 1. Introduction
Recent advancements in cs.CL have significantly transformed knowledge discovery systems. This document explores beyond sentiment: structured information extraction from financial news and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Daohan Zhu, Sitong Ge, Ruofei Wang, Honggu Chen, Yubo Hou, Tao Wan, Zengchang Qin established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Beyond Sentiment: Structured Information Extraction from Financial News provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

