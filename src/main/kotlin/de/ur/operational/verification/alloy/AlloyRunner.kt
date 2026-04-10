package de.ur.operational.verification.alloy

import de.ur.operational.verification.model.PRiMEAnnotation
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

data class AlloyModel(
    val materials: List<MaterialSpec>,
    val activities: List<ActivitySpec>,
    val inventories: List<InventorySpec>,
    val gateways: List<GatewaySpec>,
    val toolRequirements: List<ToolReqSpec>
)

data class MaterialSpec(val name: String, val initial: Int = 100)
data class ActivitySpec(val id: String, val consumes: Map<String, Int> = emptyMap(), val produces: Map<String, Int> = emptyMap())
data class InventorySpec(val materials: Map<String, Int>)
data class GatewaySpec(val id: String, val branches: List<String>)
data class ToolReqSpec(val activityId: String, val toolName: String)

data class AlloyResult(
    val l1Holds: Boolean?,
    val l5Holds: Boolean?,
    val l1Counterexample: String?,
    val l5Counterexample: String?,
    val rawOutput: String
)

object AlloyRunner {

    private val alloyJarPath = "lib/org.alloytools.alloy.dist.jar"
    
    fun generateFromTemplate(model: AlloyModel): String {
        // Read self-contained template - checks run on whatever instances are generated
        val template = Files.readString(Paths.get("src/main/resources/alloy/prime_lifecycle.als"))
        
        // Add instance declarations based on model data
        val sb = StringBuilder()
        
        // Generate material instances
        if (model.materials.isNotEmpty()) {
            sb.append("-- Material instances\n")
            model.materials.forEach { m ->
                val name = m.name.replace(" ", "_")
                sb.append("one sig M_$name in Material {}\n")
            }
        }
        
        // Generate activity instances with consume/produce relations
        if (model.activities.isNotEmpty()) {
            sb.append("\n-- Activity instances\n")
            model.activities.forEachIndexed { idx, a ->
                sb.append("one sig A_$idx in Activity {}\n")
                // Add consumes/produces facts for this activity
                a.consumes.forEach { (matName, qty) ->
                    val mname = matName.replace(" ", "_")
                    sb.append("fact { A_$idx.consumes = M_$mname -> $qty }\n")
                }
                a.produces.forEach { (matName, qty) ->
                    val mname = matName.replace(" ", "_")
                    sb.append("fact { A_$idx.produces = M_$mname -> $qty }\n")
                }
            }
        }
        
        // Generate inventory instances (single inventory, define initial via fact)
        if (model.inventories.isNotEmpty()) {
            sb.append("\n-- Inventory instances\n")
            sb.append("one sig GlobalInv in Inventory {}\n")
            val inv = model.inventories.first()
            inv.materials.entries.forEach { entry ->
                val matName = entry.key.replace(" ", "_")
                val qty = entry.value
                sb.append("fact { GlobalInv.initial = M_$matName -> $qty }\n")
            }
        }
        
        // Generate tool instances
        val tools = model.toolRequirements.map { it.toolName.replace(" ", "_") }.distinct()
        if (tools.isNotEmpty()) {
            sb.append("\n-- Tool instances\n")
            tools.forEach { t ->
                sb.append("one sig T_$t in ExclusiveTool {}\n")
            }
        }
        
        // Generate tool requirement relations - only for activities that have tools
        if (model.toolRequirements.isNotEmpty()) {
            sb.append("\n-- Tool requirements\n")
            model.toolRequirements.forEachIndexed { idx, tr ->
                val tname = tr.toolName.replace(" ", "_")
                // activityIdx comes from the task ID mapping, not sequential index
                val activityIdx = model.activities.indexOfFirst { it.id == tr.activityId }.takeIf { it >= 0 } ?: idx
                sb.append("one sig TR_$idx in ToolRequirement {}\n")
                sb.append("fact { TR_$idx.activity = A_$activityIdx }\n")
                sb.append("fact { TR_$idx.tool = T_$tname }\n")
            }
        }
        
        // Generate parallel gateway if needed
        if (model.activities.size >= 2) {
            sb.append("\n-- Parallel gateway\n")
            sb.append("one sig PG in ParallelGateway {}\n")
            (0 until model.activities.size).forEach { idx ->
                sb.append("fact { PG.branches = PG.branches + A_$idx }\n")
            }
        }
        
        // Only check rules that apply to this model
        // If model has tools AND parallel branches -> check L5, if has consumption -> check L1
        val hasParallelBranches = model.gateways.isNotEmpty() || model.activities.size >= 2
        val hasConsumption = model.activities.any { it.consumes.isNotEmpty() }
        val hasTools = model.toolRequirements.isNotEmpty() && hasParallelBranches
        
        val checks = mutableListOf<String>()
        if (hasConsumption) {
            checks.add("check { not L1_holds } for 5")
        }
        if (hasTools) {
            checks.add("check { not L5_holds } for 5")
        }
        
        checks.forEach { sb.append("\n$it\n") }
        
        return template + "\n" + sb
    }
    
    private fun parseBpmnToAlloyModel(annotations: List<PRiMEAnnotation>, taskIds: List<String>, initial: Map<String, Int> = emptyMap()): AlloyModel {
        val matSet = mutableSetOf<String>()
        val materials = mutableListOf<MaterialSpec>()
        
        for (ann in annotations) {
            for (m in ann.requirements.materials) {
                if (m.materialName.isNotBlank() && m.materialName !in matSet) {
                    matSet.add(m.materialName)
                    materials.add(MaterialSpec(m.materialName))
                }
            }
        }
        
        val activities = annotations.zip(taskIds).map { (ann, tid) ->
            val c = ann.requirements.materials.filter { it.requiredQuantity > 0 }.associate { it.materialName to it.requiredQuantity.toInt() }
            ActivitySpec(tid, c, emptyMap())
        }
        
        // Only include tools if there are actual tools in the annotations
        val tools = annotations.zip(taskIds).flatMap { (ann, tid) ->
            ann.requirements.tools.filter { it.toolType.isNotBlank() }.map { ToolReqSpec(tid, it.toolType) }
        }
        
        val inventories = listOf(InventorySpec(initial.ifEmpty { materials.associate { it.name to 100 } }))
        
        return AlloyModel(materials, activities, inventories, emptyList(), tools)
    }
    
    fun runAlloy(annotations: List<PRiMEAnnotation>, taskIds: List<String>, initialInventory: Map<String, Int> = emptyMap(), testName: String = "default"): AlloyResult {
        val model = parseBpmnToAlloyModel(annotations, taskIds, initialInventory)
        val alloyCode = generateFromTemplate(model)
        
        // Use unique filename per test in build directory
        val outputFile = File("build/reports/verification/alloy/alloy_model_$testName.als")
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(alloyCode)
        
        val jarFile = File(alloyJarPath)
        if (!jarFile.exists()) {
            return AlloyResult(
                l1Holds = null,
                l5Holds = null,
                l1Counterexample = "Alloy JAR not found at $alloyJarPath",
                l5Counterexample = null,
                rawOutput = "Alloy not installed - model written to ${outputFile.absolutePath}"
            )
        }
        
return try {
            val jarFile = File(alloyJarPath)
            if (!jarFile.exists()) {
                return AlloyResult(
                    l1Holds = null,
                    l5Holds = null,
                    l1Counterexample = "Alloy JAR not found at $alloyJarPath",
                    l5Counterexample = null,
                    rawOutput = "Alloy not installed - model written to ${outputFile.absolutePath}"
                )
            }
            
            val process = ProcessBuilder(
                "java", "-jar", alloyJarPath, "exec", "-f", outputFile.absolutePath
            ).redirectErrorStream(true).start()
            
            val output = process.inputStream.bufferedReader().readText() + "\n" + process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            // Read counterexample BEFORE cleanup (race condition fix - waitFor ensures folder is ready)
            val baseName = outputFile.nameWithoutExtension
            val baseDir = File(baseName)
            
            // Read receipt.json (structured data)
            val receiptFile = File("$baseName/receipt.json")
            val receiptJson = if (receiptFile.exists()) {
                try { receiptFile.readText() } catch (e: Exception) { null }
            } else null
            
            // NOW cleanup after reading
            // File(baseName).deleteRecursively()
            
            // DEBUG
            System.err.println("ALLOY output: $output")
            
            // Format: "00. check not(L1_holds) ... UNSAT" = L1 holds (no violation)
            //        "00. check not(L1_holds) ... SAT" = L1 violated (counterexample found)
            val lines = output.lines()
            
// Find check output - could be 00 (first check) or 01 (second)
            val line00 = lines.find { it.trim().startsWith("00.") }
            val line01 = lines.find { it.trim().startsWith("01.") }
            
            // If only 00 exists, use for whichever rule was checked
            // UNSAT = holds TRUE, SAT = holds FALSE
            val check00Result = line00?.contains("UNSAT") == true
            val check01Result = line01?.contains("UNSAT") == true
            
            // L1 holds if check00 is UNSAT (passes), fails if SAT
            val l1Holds = check00Result
            
            // L5 holds if check01 is UNSAT (passes), but if only one check (line00) exists,
            // it was L5 so use that result
            val l5Holds = if (line01 != null) check01Result else check00Result
            
            // Read solution - use same check file for both L1 and L5 when there's only one check
            val mdContent = if (!l1Holds || !l5Holds) {
                val folder = File("alloy_model_${testName.replace(" ", "_")}")
                folder.listFiles()?.find { it.name.contains("check\$1") && it.extension == "md" }?.readText()
            } else null
            
            val l1Counterexample = if (!l1Holds) mdContent?.let { parseCounterexample(it, "L1") } else null
            val l5Counterexample = if (!l5Holds) mdContent?.let { parseCounterexample(it, "L5") } else null
            
            AlloyResult(
                l1Holds = l1Holds,
                l5Holds = l5Holds,
                l1Counterexample = l1Counterexample,
                l5Counterexample = l5Counterexample,
                rawOutput = output
            )
        } catch (e: Exception) {
            AlloyResult(
                l1Holds = null,
                l5Holds = null,
                l1Counterexample = "Error running Alloy: ${e.message}",
                l5Counterexample = null,
                rawOutput = "Exception: ${e.message}"
            )
        }
    }
    
    private fun parseCounterexample(md: String, rule: String): String {
        val lines = md.lines()
        
        if (rule == "L1") {
            // For L1: find Activity instances (depletion)
            val activityViolations = lines.mapNotNull { line ->
                if (line.contains("Activity$") && line.contains("│")) {
                    val match = Regex("""Activity\$(\d+)""").find(line)
                    match?.let { "Activity${it.groupValues[1]}" }
                } else null
            }.distinct()
            
            return if (activityViolations.isNotEmpty()) {
                "L1: ${activityViolations.joinToString(", ")} (depletion)"
            } else {
                "L1 violation (depletion)"
            }
        } else {
            // For L5: find ToolRequirement instances
            val toolViolations = lines.mapNotNull { line ->
                if (line.contains("ToolRequirement$") && line.contains("│")) {
                    val match = Regex("""ToolRequirement\$(\d+)""").find(line)
                    match?.let { "ToolRequirement${it.groupValues[1]}" }
                } else null
            }.distinct()
            
            return if (toolViolations.isNotEmpty()) {
                "L5: ${toolViolations.joinToString(", ")} (parallel tool)"
            } else {
                "L5 violation (parallel tool)"
            }
        }
    }
    
    private fun extractCounterexample(output: String, rule: String): String? {
        // When SAT, the entire output after the check line contains the counterexample
        // Extract lines around "SAT" which indicate violation found
        val lines = output.lines()
        val satLines = lines.filter { it.contains("SAT") }
        return if (satLines.isNotEmpty()) {
            "Violation found:\n" + satLines.joinToString("\n")
        } else null
    }
}