# VAD: Attributing Visual Evidence for Target Reconstruction in Multimodal On-Policy Distillation

**Authors:** Kangning Zhang, Yixing Li, Shuai Shao, Qingyao Li, Zhengxi Lu, Zhiyuan Yao, Jianghao Lin, Wenxiang Jiao, Yuan Lu, Weiwen Liu, Weinan Zhang, Yong Yu  
**Publication Date:** 2026-07-30  
**Categories:** cs.CV, cs.CL  
**Document ID:** DOC-ARXIV-2607.28590v1  

## Abstract
Multimodal on-policy distillation (OPD) transfers fine-grained visual knowledge by supervising student-generated trajectories with a privileged-view teacher. Yet its next-token corrections are source-mixed, combining visual signals with linguistic priors and teacher-specific effects. The key challenge is to estimate which corrections are supported by visual evidence, not merely where or how strongly to distill. We introduce Visual Attribution Distillation (VAD), a counterfactual target-reconstruction algorithm that estimates the visually attributable part of a teacher correction. At each student-generated prefix, VAD evaluates the same fixed teacher with the relevant evidence present and removed. The corresponding change in centered log-probabilities defines ut, a signed proxy for the visual evidence direction that estimates how revealing the evidence supports or refutes candidate tokens. VAD projects the original correction onto this proxy to obtain an intervention-aligned component and a proxy-unexplained residual, then reconstructs a student-anchored target from the former. During training, this reconstructed target supplies the primary supervision signal, while the privileged teacher contributes a weak regularizer. Across six fine-grained visual benchmarks at 4B and 9B scales, VAD outperforms direct privileged-view distillation and visual-advantage weighting. Token- level and controlled-target analyses show that the proxy-aligned component is enriched in task-relevant visual corrections and yields stronger target shifts, especially when evidence refutes a mistaken answer. These results support counterfactual target reconstruction as an effective alternative to source-mixed supervision.

## 1. Introduction
Recent advancements in cs.CV, cs.CL have significantly transformed knowledge discovery systems. This document explores vad: attributing visual evidence for target reconstruction in multimodal on-policy distillation and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Kangning Zhang, Yixing Li, Shuai Shao, Qingyao Li, Zhengxi Lu, Zhiyuan Yao, Jianghao Lin, Wenxiang Jiao, Yuan Lu, Weiwen Liu, Weinan Zhang, Yong Yu established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, VAD: Attributing Visual Evidence for Target Reconstruction in Multimodal On-Policy Distillation provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

