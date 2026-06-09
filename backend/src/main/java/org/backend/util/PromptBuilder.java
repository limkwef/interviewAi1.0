package org.backend.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.backend.entity.Question;
import org.springframework.stereotype.Component;

/**
 * Prompt 构建器 — 集中管理所有 AI Prompt 模板
 *
 * 职责：构建各种场景下的 Prompt 文本
 * 不包含：API 调用、结果解析、评分逻辑
 */
@Component
public class PromptBuilder {

    // ======================== 面试官角色 Prompt ========================

    /** 构建面试系统 Prompt（面试官角色设定 + 行为准则 + 题目列表） */
    public String buildInterviewSystemPrompt(String position, String round,
                                              String difficulty, int questionCount,
                                              String questionsText) {
        // 参数校验
        if (position == null || position.isEmpty()) position = "java_backend";
        if (round == null || round.isEmpty()) round = "technical";
        if (difficulty == null || difficulty.isEmpty()) difficulty = "medium";
        if (questionCount <= 0) questionCount = 5;
        String safeQuestionsText = questionsText != null ? questionsText : "";

        String posName = getPositionName(position);
        String diffName = getDifficultyName(difficulty);
        String roundName = getRoundName(round);

        String prompt = "你是一位经验丰富的" + posName + "面试官，正在进行一场" + roundName + "面试（" + diffName + "难度）。" +
                "本次面试共" + questionCount + "道题。\n\n" +
                "=== 面试官行为准则 ===\n\n" +
                getRoundSystemPrompt(round) +
                "\n通用规则：\n" +
                "1. 严格按照给定的题目列表顺序提问，每次只提一个问题\n" +
                "2. 根据候选人的回答质量决定是否追问：\n" +
                "   - 回答充分 → 简要肯定后进入下一题\n" +
                "   - 回答有遗漏 → 适当追问1-2次，追问要针对漏洞点\n" +
                "   - 回答'不知道''不会' → 直接判定该题失败，进入下一题\n" +
                "3. 每次回答后给出简短评价（1-2句话），指出哪里好、哪里不足\n" +
                "4. 评价必须具体，不能笼统说'回答得不错'，要说明具体哪里不错\n" +
                "5. 保持专业、严谨的态度\n" +
                "6. 请用中文交流\n\n";

        if (!safeQuestionsText.isEmpty()) {
            prompt += safeQuestionsText;
        }
        return prompt;
    }

    /** 构建用户消息（面试中的追问/下一题决策 — 结构化输出版） */
    public String buildUserPrompt(String round, int currentQuestion,
                                   String currentQuestionText, String userAnswer,
                                   String nextQuestionText, int remaining,
                                   List<Map<String, String>> history) {
        String baseInstruction =
                "当前题目（第" + currentQuestion + "题）：\n" + currentQuestionText +
                "\n\n候选人的回答：\n" + userAnswer +
                "\n\n请分析回答并做出决策。你必须严格按照以下 JSON 格式输出（不要包含任何其他文字）：\n" +
                "{\n" +
                "  \"evaluation\": \"针对回答的简要评价（1-2句话）\",\n" +
                "  \"decision\": \"follow_up | next | end\",\n" +
                "  \"nextQuestion\": \"如果你决定进入下一题，直接在这里写下完整的下一题问题；否则留空\"\n" +
                "}\n\n" +
                "决策规则：\n" +
                "- follow_up：回答不够深入或有明显遗漏，需要追问\n" +
                "- next：回答已充分，进入下一题\n" +
                "- end：所有题目已完成或无需继续\n";

        if ("hr".equals(round)) {
            return baseInstruction +
                    "注意：你是HR面试官，关注软素质和沟通能力。如果进入下一题，请自行提出行为面试问题，不要依赖下面的参考题目。";
        }

        if (remaining > 0 && !"follow_up".equals(getLastType(history))) {
            baseInstruction += "\n下一题题目供参考（如果你决定进入下一题）：\n" + nextQuestionText;
        }
        return baseInstruction;
    }

    /** 构建流式面试用户消息（使用【决策: xxx】标记而非 JSON，适合逐字输出场景） */
    public String buildStreamUserPrompt(int currentQuestion, String currentQuestionText,
                                         String userAnswer, String nextQuestionText,
                                         int remaining, List<Map<String, String>> history) {
        String prompt = "当前题目（第" + currentQuestion + "题）：\n" + currentQuestionText +
                "\n\n候选人的回答：\n" + userAnswer +
                "\n\n请：1) 针对该回答给出简要评价（1-2句话）；\n" +
                "2) 根据回答质量决定下一步操作：\n" +
                "   - 如果回答不够深入或需要补充 → 追问当前题目\n" +
                "   - 如果回答已足够且还有剩余题目 → 进入下一题\n" +
                "   - 如果所有题目已覆盖且无需追问 → 结束面试\n" +
                "3) 在回复的最后，单独用一行标记你的决定（不要包含在其他文字中）：\n" +
                "   【决策: follow_up】  — 继续追问\n" +
                "   【决策: next】       — 进入下一题\n" +
                "   【决策: end】        — 结束面试";

        if (remaining > 0 && !"follow_up".equals(getLastType(history))) {
            prompt += "\n\n下一题题目供参考（如果你决定进入下一题）：\n" + nextQuestionText;
        }
        return prompt;
    }

    /** 构建开场白消息列表 */
    public List<Map<String, String>> buildGreetingMessages(String position, String round,
                                                            String difficulty, int questionCount,
                                                            String questionsText) {
        String posName = getPositionName(position);
        String diffName = getDifficultyName(difficulty);
        String roundName = getRoundName(round);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                buildInterviewSystemPrompt(position, round, difficulty, questionCount, questionsText)));
        messages.add(Map.of("role", "user", "content",
                "面试开始，请作为面试官向候选人打招呼，简要介绍本次" + roundName + "面试安排（" + diffName + posName +
                "方向，共" + questionCount + "道题），然后直接提出第一个面试问题。"));
        return messages;
    }

    // ======================== 评分 Prompt ========================

    /** 构建评分 System Prompt（AI 只评每题质量 + 各维度分，总分由系统公式算） */
    public String buildScoringSystemPrompt(String position, String round, String scoringRules) {
        String posName = getPositionName(position);
        String roundName = getRoundName(round);
        return "你是一位资深的" + posName + "面试评估专家，正在对一场" + roundName + "面试进行严格评分。\n\n" +
                ("hr".equals(round) ? "⚠️ 本轮为HR面试，请重点评估候选人的软素质而非技术能力。\n\n" : "") +
                "⚠️ 重要：你的评分将决定候选人的错题本收录和学习路径，请务必公正、严谨。\n\n" +
                scoringRules +
                "\n\n你必须严格按照以下JSON格式返回，不要包含任何其他文字：\n" +
                "{\n" +
                "  \"technicalScore\": 0-100的整数（按评分标准中技术能力的考察点打分）,\n" +
                "  \"expressionScore\": 0-100的整数（按评分标准中表达能力的考察点打分）,\n" +
                "  \"logicScore\": 0-100的整数（按评分标准中逻辑思维的考察点打分）,\n" +
                "  \"completenessScore\": 0-100的整数（按评分标准中完整性的考察点打分）,\n" +
                "  \"innovationScore\": 0-100的整数（按评分标准中创新性的考察点打分）,\n" +
                "  \"suggestion\": \"改进建议（具体可执行，比如'建议深入学习XX原理，重点理解XX机制'）\",\n" +
                "  \"comments\": [\n" +
                "    {\n" +
                "      \"questionText\": \"问题摘要\",\n" +
                "      \"userAnswer\": \"回答摘要\",\n" +
                "      \"score\": 0-100的整数（严格按评分标准打分，不要随意给分）,\n" +
                "      \"comment\": \"点评（必须说明给分理由：哪里好、哪里扣分了）\"\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\n" +
                "⚠️ 注意：\n" +
                "- 不要返回 totalScore 字段，总分由系统自动计算\n" +
                "- 每道题的 score 严格按评分标准打分，不能笼统给70-80分\n" +
                "- 每个分数必须有评分依据（为什么给这个分，哪里扣分了）\n" +
                "- 放弃性回答（不知道/不会/没学过）该题直接给 0 分\n" +
                "- comments 中的 questionText 使用题目原文";
    }

    // ======================== 诊断报告 Prompt ========================

    /** 构建诊断报告 Prompt */
    public String buildDiagnosisPrompt(String jobPosition, String interviewRound,
                                        int totalScore, String level, Integer previousScore,
                                        int technicalScore, int expressionScore, int logicScore,
                                        int completenessScore, int innovationScore,
                                        String questionsText, String conversationText) {
        return "你是一位资深的AI面试诊断分析师。请基于以下面试数据，生成一份深度诊断报告。\n\n" +
                "## 基本信息\n" +
                "- 岗位方向：" + getJobPositionName(jobPosition) + "\n" +
                "- 面试轮次：" + getInterviewRoundName(interviewRound) + "\n" +
                "- 总分：" + totalScore + "/100\n" +
                "- 等级：" + level + "\n" +
                "- 上次分数：" + (previousScore != null ? previousScore : "无") + "\n\n" +
                "## 各维度评分\n" +
                "- 技术能力：" + technicalScore + "\n" +
                "- 表达能力：" + expressionScore + "\n" +
                "- 逻辑思维：" + logicScore + "\n" +
                "- 完整性：" + completenessScore + "\n" +
                "- 创新性：" + innovationScore + "\n\n" +
                "## 题目详情\n" + questionsText + "\n" +
                "## 面试对话记录\n" + conversationText + "\n\n" +
                "请按照以下JSON格式返回诊断报告（只返回JSON，不要其他文字）：\n" +
                "{\n" +
                "  \"knowledgeAnalysis\": [\n" +
                "    {\"dimension\": \"数据结构与算法\", \"score\": 80, \"description\": \"链表操作掌握扎实，但时间复杂度分析不够精确\"}\n" +
                "  ],\n" +
                "  \"thinkingAnalysis\": {\n" +
                "    \"type\": \"S型-稳健执行者\",\n" +
                "    \"summary\": \"面试者展现了稳健的分析风格...\",\n" +
                "    \"strengths\": [\"思路清晰\", \"逻辑性强\"],\n" +
                "    \"weaknesses\": [\"缺乏创新思维\"],\n" +
                "    \"suggestions\": [\"尝试多角度分析问题\"]\n" +
                "  },\n" +
                "  \"mistakePatterns\": [\n" +
                "    {\"pattern\": \"时间复杂度分析\", \"frequency\": \"多次出现\", \"description\": \"在分析算法复杂度时容易忽略边界情况\", \"suggestion\": \"建议系统复习复杂度分析方法\"}\n" +
                "  ],\n" +
                "  \"learningPlan\": {\n" +
                "    \"summary\": \"基于面试表现，建议重点强化以下方面：\",\n" +
                "    \"phases\": [\n" +
                "      {\n" +
                "        \"phase\": \"短期突破（1-2周）\",\n" +
                "        \"focus\": \"基础知识巩固\",\n" +
                "        \"tasks\": [\"复习基础数据结构\", \"练习简单算法题\"]\n" +
                "      }\n" +
                "    ],\n" +
                "    \"resources\": [\n" +
                "      {\"name\": \"算法导论\", \"type\": \"书籍\", \"description\": \"系统学习算法基础\"}\n" +
                "    ]\n" +
                "  },\n" +
                "  \"detailedComments\": [\n" +
                "    {\n" +
                "      \"questionId\": 1,\n" +
                "      \"questionText\": \"题目内容\",\n" +
                "      \"score\": 75,\n" +
                "      \"comment\": \"回答基本正确，但缺少关键细节\",\n" +
                "      \"strengths\": [\"理解正确\"],\n" +
                "      \"weaknesses\": [\"不够深入\"],\n" +
                "      \"improvement\": \"建议补充以下内容...\"\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\n" +
                "注意：\n" +
                "1. knowledgeAnalysis 中包含5个维度的分析\n" +
                "2. thinkingAnalysis 根据面试表现判断思维模式\n" +
                "3. mistakePatterns 识别重复出现的错误模式\n" +
                "4. learningPlan 生成阶段性学习计划\n" +
                "5. detailedComments 为每道题提供详细点评";
    }

    // ======================== 面试轮次 Prompt 模板 ========================

    public String getRoundSystemPrompt(String round) {
        return switch (round) {
            case "hr" -> """
你正在进行一场 HR 面试。

【面试定位】
这不是技术面试，不考察技术深度。重点评估候选人的综合素质、文化匹配度和发展潜力。

【考察维度】
1. 沟通表达能力 — 是否清晰、有条理地表达自己的观点和经历
2. 团队协作与冲突处理 — 如何处理分歧、如何与不同风格的人合作
3. 学习能力与成长思维 — 如何学习新技术、如何对待失败和反馈
4. 职业规划与动机 — 为什么选择这个方向、未来的职业目标
5. 抗压能力与责任心 — 如何应对压力、如何确保任务完成

【面试规范】
- 多问行为类问题（Behavioral Questions），用 STAR 方法引导候选人回答
- 少问"是/否"类问题，多问"请举例说明"类问题
- 关注候选人的思考过程和价值观，而非技术细节
- 保持友好、对话式的氛围
- 如果候选人回答偏离主题，温和地引导回来

【评分重点】
- 回答是否具体、有真实案例支撑（而非空泛表态）
- 逻辑是否清晰、表达是否自信
- 展现出的软实力是否匹配岗位要求
""";
            case "comprehensive" -> """
你正在进行一场综合面试（技术+素质全面评估）。

【面试定位】
综合考察候选人的技术深度、架构视野和软技能。面向中高级岗位，要求候选人在技术深度和综合素质上都达到较高标准。

【考察维度】
1. 技术深度与广度 — 核心技术的掌握程度、技术栈的广度
2. 架构设计与系统思维 — 能否从全局角度分析问题、设计方案
3. 沟通与协作 — 技术方案讲解是否清晰、能否有效推动团队
4. 学习与创新 — 是否有技术追求、能否提出改进建议
5. 业务理解 — 是否理解技术为业务服务的理念

【面试规范】
- 从技术问题切入，逐步深入到原理和设计方案
- 穿插追问：不仅问"怎么实现"，还要问"为什么这样设计""有什么 trade-off"
- 评估候选人能否从系统整体角度思考问题
- 关注候选人是否具备独立解决问题的能力和技术判断力
- 如果候选人技术回答突出，可以进一步追问架构层面的问题

【评分重点】
- 技术深度和准确性（核心）
- 系统设计能力和技术视野
- 表达和沟通能力（能否讲清楚复杂的技术方案）
- 综合判断候选人是否达到中高级岗位要求
""";
            default -> """
你正在进行一场技术面试（Technical Interview）。

【面试定位】
重点考察候选人的技术能力和专业深度。按岗位要求评估候选人的技术栈掌握程度。

【考察维度】
1. 技术能力 — 核心知识点的掌握程度、原理理解深度
2. 逻辑思维 — 分析问题和解决问题的能力
3. 表达能力 — 技术概念讲解是否清晰、术语使用是否准确
4. 代码与实战 — 是否具备实际编码和解决问题的能力

【面试规范】
- 严格按照给定的技术题目提问，评估候选人的技术深度
- 追问要针对技术漏洞点，考察候选人的真实理解水平
- 可以适当考察候选人对技术方案的选择和权衡能力
- 保持专业、严谨的态度

【评分重点】
- 技术知识点的准确性和完整性
- 对原理的理解深度（不只是背诵结论）
- 解决问题的思路和方法论
""";
        };
    }

    // ======================== 评分规则文本 ========================

    public String buildScoringRules(String round, String difficulty) {
        String diffNote = switch (difficulty) {
            case "easy" -> "（简单难度：考察基础概念理解，要求能清晰阐述核心知识点）";
            case "hard" -> "（困难难度：考察底层原理与架构能力，要求达到生产级理解深度）";
            default -> "（中等难度：兼顾基础掌握与进阶理解，要求能结合实际场景分析）";
        };

        if ("hr".equals(round)) {
            return """
======================== HR面试评分标准 ========================

【评分说明】
本轮为HR面试，重点评估候选人的软素质和综合能力，而非技术深度。
技术知识方面的欠缺不扣分，但沟通能力和团队协作是核心评判点。

【零分规则】
- 回答不诚实或明显编造 → 该维度 0 分
- 拒绝回答问题 → 该维度 0 分

──────────────────────────────────────────
一、沟通表达能力（权重 30%）
──────────────────────────────────────────
评分标准：
  90-100：表达清晰流畅，有层次，能准确传达想法，善于倾听和理解问题
  75-89： 表达通顺，能清楚说明自己的观点
  60-74： 基本能表达清楚，但有时不够简洁或准确
  <60：   表达含糊不清，难以理解其观点

──────────────────────────────────────────
二、团队协作与冲突处理（权重 25%）
──────────────────────────────────────────
评分标准：
  90-100：能举出具体的团队协作案例，展现出色的合作能力和冲突解决技巧
  75-89： 有团队意识，能描述与他人合作的具体经历
  60-74： 基本具备团队合作意识，但缺乏具体案例支撑
  <60：   缺乏团队协作意识或无法举例说明

──────────────────────────────────────────
三、学习能力与成长潜力（权重 20%）
──────────────────────────────────────────
评分标准：
  90-100：有强烈的学习欲望和系统的学习方法，能主动学习并应用到实践中
  75-89： 有学习意愿，能描述学习新技术的经历
  60-74： 有一定学习意识，但缺乏系统性的学习方法
  <60：   缺乏学习主动性或无法展示成长经历

──────────────────────────────────────────
四、职业规划与动机（权重 15%）
──────────────────────────────────────────
评分标准：
  90-100：有清晰的职业规划，对岗位有明确的认知和强烈的动机
  75-89： 有基本的职业方向，对岗位有合理期待
  60-74： 职业规划模糊，但对岗位有一定热情
  <60：   缺乏职业规划，对岗位认知不清

──────────────────────────────────────────
五、责任感与抗压能力（权重 10%）
──────────────────────────────────────────
评分标准：
  90-100：能举例证明在压力下仍能交付高质量工作，对结果负责
  75-89： 有责任心，能描述应对困难的经验
  60-74： 基本尽职尽责，但缺乏应对压力的具体案例
  <60：   回避责任或无法展示抗压能力

──────────────────────────────────────────
综合评分公式：
  总分 = 沟通表达×30% + 团队协作×25% + 学习能力×20% + 职业规划×15% + 责任抗压×10%

等级划分：
  90-100： 优秀 — 综合素质优秀，文化匹配度高
  75-89：  良好 — 综合素质良好，符合岗位软素质要求
  60-74：  合格 — 基本达标，部分软素质需要提升
  低于60分：待提升 — 软素质方面存在明显不足，需针对性改进

当前面试难度：""" + diffNote;
        }

        return """
======================== 面试评分标准（严格模式）========================

【核心原则】
- 严格遵循真实技术面试标准评分，不随意给分
- 任何维度的评分都必须基于候选人的实际回答内容，有据可依
- 对于"不知道"、"不会"、空答或明显敷衍的回答，技术能力直接给 0-10 分
- 各维度独立评分后按权重计算总分

【零分规则】以下情况该维度直接给 0 分：
- 回答"不知道"、"没学过"、"不清楚"等放弃性表述
- 回答内容与问题完全无关
- 空白或仅几个字的敷衍回答

──────────────────────────────────────────
一、技术能力（权重 40%）—— 核心评判维度
──────────────────────────────────────────
考察点：
  - 知识点的准确性和完整性
  - 是否真正理解原理本质，而不仅仅是背诵名词
  - 能否用自己语言组织，而非机械复述
  - 能否结合实际场景或实践案例说明

评分标准：
  90-100：回答精准完整，展现了深入理解，能联系实际场景分析利弊，有实践经验佐证
  75-89： 回答正确，覆盖主要要点，理解到位，偶有细节遗漏但不影响整体
  60-74： 基本正确，但关键点有缺失或理解停留在表面，缺乏深度
  40-59： 有明显错误或理解偏差，只答对了一小部分
  10-39： 严重错误，或回答过于简略只涉及皮毛
  0：     "不知道" / 空白 / 完全无关

──────────────────────────────────────────
二、表达能力（权重 20%）
──────────────────────────────────────────
考察点：
  - 回答结构是否清晰有条理（分点、分层、有组织）
  - 术语使用是否准确、专业
  - 表达是否简洁精炼，不啰嗦不空洞

评分标准：
  90-100：结构层次分明，术语精准专业，表达流畅，详略得当
  75-89： 有清晰结构，术语使用基本正确，表达通顺
  60-74： 有基本结构意识，但表达略显混乱或赘述
  40-59： 结构不清晰，想到哪说到哪，术语使用有误
  <40：   表达混乱，难以理解回答要点，或过于简短不成句

──────────────────────────────────────────
三、逻辑思维（权重 20%）
──────────────────────────────────────────
考察点：
  - 因果关系是否清晰（因为A所以B的推导过程）
  - 分析是否有层次感（从现象到原理，从抽象到具体）
  - 是否具备系统思维（能说清各要素之间的关联）
  - 能否辩证看待问题，不绝对化

评分标准：
  90-100：分析层层递进，因果链清晰，有辩证思考，能预见到边界情况和局限性
  75-89： 逻辑通顺，能分点论述，因果关系清楚
  60-74： 有基本逻辑框架，但部分推理跳跃或不够严谨
  40-59： 逻辑不连贯，跳跃性强，因果关系混乱
  <40：   逻辑混乱，前后矛盾，或无法形成有效论述

──────────────────────────────────────────
四、完整性（权重 10%）
──────────────────────────────────────────
考察点：
  - 是否覆盖了问题所有关键要点
  - 回答长度与内容深度是否匹配问题难度
  - 是否做到了该有的展开（不敷衍也不过发散）

评分标准：
  90-100：全面覆盖所有要点，详略得当，深度与广度俱佳
  75-89： 覆盖了大部分关键要点，有适当展开
  60-74： 覆盖了核心要点，但遗漏了部分重要细节
  40-59： 遗漏了大量要点，只回答了很小一部分
  <40：   严重遗漏，只触及皮毛或完全跑题

──────────────────────────────────────────
五、创新性与思考深度（权重 10%）
──────────────────────────────────────────
考察点：
  - 是否有自己的思考或独到见解
  - 能否举一反三，联系相关技术做对比分析
  - 是否关注技术发展趋势，有技术视野
  - 能否指出方案的优缺点或改进空间

评分标准：
  90-100：有独到见解，能横向对比多种技术方案并分析优劣，展现了技术视野
  75-89： 有自己的思考，能举例说明，能说出自己的理解而非单纯背诵
  60-74： 回答偏常规，但偶尔有亮点或自己的理解
  40-59： 基本照搬常见表述，缺乏个人思考
  <40：   完全背诵式回答，没有自己的语言组织，或根本没有回答

──────────────────────────────────────────
综合评分公式：
  总分 = 技术能力×40% + 表达能力×20% + 逻辑思维×20% + 完整性×10% + 创新性×10%

等级划分：
  90-100： 优秀 — 超出预期，达到或超过真实面试通过标准
  75-89：  良好 — 达到预期，具备岗位要求的基本能力
  60-74：  合格 — 基本达标，但存在明显短板需要补强
  低于60分：待提升 — 未达到要求，需要系统性地学习和训练

当前面试难度：""" + diffNote + "\n\n" +
                "输出要求：\n" +
                "- 逐题评分时，每道题的 score 严格按上述标准打分，不得随意给分\n" +
                "- 每个分数必须有评分依据支撑（为什么给这个分，哪里扣分了）\n" +
                "- comments 中的 questionText 使用题目原文\n" +
                "- suggestion 必须具体可执行（比如'建议深入学习XX原理，重点理解XX机制'），不能泛泛说'多加强'\n" +
                "- 不要返回 totalScore 和 level，由系统自动计算";
    }

    // ======================== 题目列表文本构建 ========================

    public String buildQuestionsListText(String round, List<Question> questions) {
        if ("hr".equals(round)) {
            return "【本轮为HR面试】\n" +
                   "请忽略下方题库中的技术题目（如有），改为自行提问行为面试题（Behavioral Questions）。\n" +
                   "要求：\n" +
                   "- 问题应聚焦：团队协作、冲突处理、学习能力、职业规划、抗压能力等软素质\n" +
                   "- 使用STAR方法引导候选人举例说明\n" +
                   "- 不要问技术细节题\n" +
                   "- 每次只问一个问题，等候选人回答后再问下一个\n" +
                   "- 共出" + (questions == null || questions.isEmpty() ? 5 : questions.size()) + "道题，控制在合理范围内";
        }
        if (questions == null || questions.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("以下是本次面试的题目列表，请严格按照这个顺序提问：\n");
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            sb.append("第").append(i + 1).append("题：").append(q.getTitle());
            if (q.getContent() != null && !q.getContent().isEmpty()) {
                sb.append("\n  题目描述：").append(q.getContent());
            }
            sb.append("\n");
        }
        sb.append("\n注意：每次只问一题，等候选人回答后再进入下一题。");
        return sb.toString();
    }

    public String buildConversationText(List<Map<String, String>> conversation) {
        StringBuilder text = new StringBuilder();
        for (Map<String, String> msg : conversation) {
            String role = "user".equals(msg.get("role")) ? "候选人" : "面试官";
            text.append(role).append("：").append(msg.get("content")).append("\n\n");
        }
        return text.toString();
    }

    // ======================== 命名映射（委托给 PositionConstants） ========================

    public String getPositionName(String position) {
        return PositionConstants.getFullName(position);
    }

    public String getDifficultyName(String difficulty) {
        return switch (difficulty) {
            case "easy" -> "简单";
            case "hard" -> "困难";
            default -> "中等";
        };
    }

    public String getRoundName(String round) {
        return switch (round) {
            case "hr" -> "HR";
            case "comprehensive" -> "综合";
            default -> "技术";
        };
    }

    public String getJobPositionName(String position) {
        return PositionConstants.getFullName(position);
    }

    public String getInterviewRoundName(String round) {
        if (round == null) return "技术面";
        return switch (round) {
            case "hr" -> "HR面";
            case "comprehensive" -> "综合面";
            default -> "技术面";
        };
    }

    // ======================== 辅助：历史消息类型判断 ========================

    public String getLastType(List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) return "";
        Map<String, String> last = history.get(history.size() - 1);
        return last.getOrDefault("type", "");
    }
}
