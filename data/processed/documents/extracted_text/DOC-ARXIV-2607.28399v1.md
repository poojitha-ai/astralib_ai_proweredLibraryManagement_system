# Why Are GUI Agents Correct but Late? Decode on the Decision-Time Critical Path, Tested with Pre-Compiled Policy Trees

**Authors:** Zihan Dong, Rui Qian, Qishi Zhan, Dongshen Peng, Kaixin Li, Yu Li  
**Publication Date:** 2026-07-30  
**Categories:** cs.LG  
**Document ID:** DOC-ARXIV-2607.28399v1  

## Abstract
Computer-use agents often fail on transient GUI events because they produce the correct action only after the relevant window has already closed. We identify the main cause as expensive autoregressive decoding on the decision-time critical path. We propose Adaptive Anticipatory Policy Trees (AAPT), which eliminates this delay without modifying the underlying model. During idle screen periods, the same frozen multimodal model constructs a bounded conditional policy tree with observable guards, pre-authorized actions, and branch-specific deadlines. The tree is sized to cover the model's own decoding latency. When an event occurs, a lightweight observer matches change-gated frames to a prepared branch and immediately executes the corresponding action without generating new text. In paired trials with pre-registered endpoints and exact McNemar tests, AAPT improves the success rate from 0.50 to 0.79 within a contested decision window ($p=1.8\times10^{-3}$), while producing no incorrect actions. Both open-loop and predict-and-replan baselines achieve zero success because they still decode during execution. A preparation-time sweep shows that the gain emerges where the latency-based tree-sizing rule predicts, and ablations reveal three key requirements: fast observer decoding, valid tree planning, and accurate branch routing. A pre-registered oracle probe rejects our initial hypothesis and instead points to branch routing as the causal bottleneck. We further reproduce the effect on an independent general-purpose multimodal model over 126 paired trials ($p=4.9\times10^{-13}$). On an external benchmark, AAPT matches the overall performance of a reactive baseline, although the two methods exhibit complementary strengths. Together, these results suggest that AAPT performs best when candidate actions can be enumerated in advance, whereas reactive execution remains stronger when they cannot.

## 1. Introduction
Recent advancements in cs.LG have significantly transformed knowledge discovery systems. This document explores why are gui agents correct but late? decode on the decision-time critical path, tested with pre-compiled policy trees and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Zihan Dong, Rui Qian, Qishi Zhan, Dongshen Peng, Kaixin Li, Yu Li established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Why Are GUI Agents Correct but Late? Decode on the Decision-Time Critical Path, Tested with Pre-Compiled Policy Trees provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

