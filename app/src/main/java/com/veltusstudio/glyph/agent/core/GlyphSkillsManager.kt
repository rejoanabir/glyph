package com.veltusstudio.glyph.agent.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class GlyphSkillInfo(
    val id: String,
    val name: String,
    val command: String,
    val description: String,
    val category: String,
    val path: String,
    val isCustom: Boolean = false
)

class GlyphSkillsManager private constructor(private val context: Context) {

    companion object {
        const val SKILLS_STORAGE_DIR = "/storage/emulated/0/antigram/skills"

        @Volatile
        private var instance: GlyphSkillsManager? = null

        fun getInstance(context: Context): GlyphSkillsManager {
            return instance ?: synchronized(this) {
                instance ?: GlyphSkillsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val builtinSkills = listOf(
        GlyphSkillInfo(
            id = "autonomous-software-company",
            name = "Autonomous Software Company",
            command = "/autonomous-software-company",
            description = "Coordinates 15 specialized sub-agents covering Strategy, Architecture, UI/UX, Motion Graphics, Engineering, Database, QA, Security, and DevOps end-to-end.",
            category = "ENTERPRISE",
            path = "$SKILLS_STORAGE_DIR/autonomous-software-company/SKILL.md"
        ),
        GlyphSkillInfo(
            id = "auto-shorts-factory",
            name = "Auto Shorts Factory",
            command = "/auto-shorts-factory",
            description = "Automated YouTube Shorts pipeline: Highlight Scout, Split-Screen Editor (webcam/gameplay), Caption & Overlay Artist, FFmpeg Renderer, and SEO Uploader.",
            category = "MULTIMEDIA",
            path = "$SKILLS_STORAGE_DIR/auto-shorts-factory/SKILL.md"
        ),
        GlyphSkillInfo(
            id = "motion-graphics",
            name = "Motion Graphics Studio",
            command = "/motion-graphics",
            description = "Kinetic typography, Disney animation principles, Lottie animations, and visual storytelling inspired by MK12 and Buck studios.",
            category = "CREATIVE",
            path = "$SKILLS_STORAGE_DIR/motion-graphics/SKILL.md"
        ),
        GlyphSkillInfo(
            id = "retrieving-developer-knowledge",
            name = "Developer Knowledge Synthesizer",
            command = "/developer-knowledge",
            description = "Deep search and synthesis of official Google developer documentation across Google Cloud, AI/Gemini, Android, Web, and Flutter.",
            category = "RESEARCH",
            path = "builtin://retrieving-developer-knowledge/SKILL.md"
        ),
        GlyphSkillInfo(
            id = "run-workflow",
            name = "Dynamic Workflow Orchestrator",
            command = "/run-workflow",
            description = "Author and execute dynamic multi-agent Python workflows fanning out over tasks with parallel agents, pipelines, and aggregators.",
            category = "ORCHESTRATION",
            path = "builtin://run-workflow/SKILL.md"
        ),
        GlyphSkillInfo(
            id = "goal",
            name = "Overnight Goal Runner",
            command = "/goal",
            description = "Executes complex long-running tasks autonomously with extensive verification and self-healing cycles until the goal is fully achieved.",
            category = "CORE",
            path = "builtin://goal/SKILL.md"
        ),
        GlyphSkillInfo(
            id = "plan",
            name = "Deep Architecture Planner",
            command = "/plan",
            description = "Strict architectural planning and step-by-step phased execution roadmap before modifying production code.",
            category = "CORE",
            path = "builtin://plan/SKILL.md"
        ),
        GlyphSkillInfo(
            id = "grill-me",
            name = "Interactive Alignment Interviewer",
            command = "/grill-me",
            description = "Conducts an interactive technical interview to clarify tradeoffs, resolve ambiguous edge cases, and align on architecture.",
            category = "CORE",
            path = "builtin://grill-me/SKILL.md"
        )
    )

    private val _skills = MutableStateFlow<List<GlyphSkillInfo>>(builtinSkills)
    val skills: StateFlow<List<GlyphSkillInfo>> = _skills.asStateFlow()

    suspend fun refreshSkills() = withContext(Dispatchers.IO) {
        val storageDir = File(SKILLS_STORAGE_DIR)
        val discovered = mutableListOf<GlyphSkillInfo>()
        discovered.addAll(builtinSkills)

        if (storageDir.exists() && storageDir.isDirectory) {
            val folders = storageDir.listFiles { f -> f.isDirectory } ?: emptyArray()
            for (folder in folders) {
                val skillMd = File(folder, "SKILL.md")
                if (skillMd.exists()) {
                    val id = folder.name
                    val alreadyInBuiltin = builtinSkills.any { it.id == id }
                    if (!alreadyInBuiltin) {
                        val firstFewLines = try {
                            skillMd.readLines().take(10).joinToString(" ")
                        } catch (_: Exception) { "" }
                        discovered.add(
                            GlyphSkillInfo(
                                id = id,
                                name = id.split("-", "_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                                command = "/$id",
                                description = firstFewLines.ifBlank { "User-defined skill found in storage directory." }.take(140),
                                category = "CUSTOM",
                                path = skillMd.absolutePath,
                                isCustom = true
                            )
                        )
                    }
                }
            }
        }
        _skills.value = discovered
    }

    suspend fun readSkillMarkdown(skill: GlyphSkillInfo): String = withContext(Dispatchers.IO) {
        val file = File(skill.path)
        if (file.exists()) {
            try {
                return@withContext file.readText()
            } catch (e: Exception) {
                return@withContext "Error reading skill specification: ${e.message}"
            }
        }

        // Builtin fallback templates
        when (skill.id) {
            "autonomous-software-company" -> """
                # Autonomous Software Company
                Acts as a complete, 15-department autonomous software company inside a single skill.
                A CEO / Orchestrator dynamically activates only the specialized sub-agents needed:
                - Strategy & Architecture
                - UI/UX Design & Motion Graphics
                - Frontend & Mobile Engineering
                - Database & Cloud Infrastructure
                - QA Automation & Penetration Security
                - GitHub CI/CD & DevOps Automation
            """.trimIndent()
            "auto-shorts-factory" -> """
                # Auto Shorts Factory
                Automated end-to-end YouTube Shorts production pipeline:
                1. Highlight Scout: Detects hype peaks via audio analysis.
                2. Split-Screen Editor: 9:16 vertical reframe with facecam top and gameplay bottom.
                3. Caption Artist: Dynamic word-by-word animated subtitles.
                4. Video Renderer: Fast FFmpeg hardware-accelerated encode.
                5. YouTube Uploader: Publishes directly with optimized tags.
            """.trimIndent()
            "motion-graphics" -> """
                # Motion Graphics Studio
                World-class animation principles combining Disney 12 principles, kinetic typography of MK12, and visual storytelling of Buck and Ordinary Folk.
                Transforms static ideas into fluid visual experiences with timing, easing, and micro-interactions.
            """.trimIndent()
            else -> """
                # ${skill.name} (${skill.command})
                Category: ${skill.category}
                
                ${skill.description}
                
                Execute this skill in Workbench using: `${skill.command}`
            """.trimIndent()
        }
    }

    suspend fun createCustomSkill(
        name: String,
        category: String,
        description: String,
        instructions: String
    ): Boolean = withContext(Dispatchers.IO) {
        val slug = name.lowercase().trim().replace(Regex("[^a-z0-9]+"), "-")
        val dir = File(SKILLS_STORAGE_DIR, slug)
        if (!dir.exists()) dir.mkdirs()

        val skillFile = File(dir, "SKILL.md")
        val content = """
            ---
            name: $name
            description: $description
            category: $category
            ---
            
            # $name
            
            $instructions
        """.trimIndent()

        try {
            skillFile.writeText(content)
            refreshSkills()
            true
        } catch (_: Exception) {
            false
        }
    }
}
