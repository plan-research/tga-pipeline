package org.plan.research.tga.analysis

import kotlinx.serialization.encodeToString
import org.plan.research.tga.analysis.metrics.MetricsProvider
import org.plan.research.tga.core.benchmark.json.JsonBenchmarkProvider
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.resolve
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants
import javax.swing.WindowConstants
import kotlin.io.path.bufferedWriter
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText


fun main() {
    val propertiesFile = Paths.get("properties.json")
    val properties = when {
        propertiesFile.exists() -> getJsonSerializer(pretty = true).decodeFromString<List<Properties>>(propertiesFile.readText())
            .associateBy { it.benchmark }.toMutableMap()

        else -> mutableMapOf()
    }
    val metricsProvider = MetricsProvider(Paths.get("metrics2.json"), Paths.get("cyclomatic-complexity.txt"))

    var shouldStop = false

    for (benchmark in JsonBenchmarkProvider(Paths.get("benchmarks/benchmarks.json")).benchmarks()) {
        val canContinue = AtomicBoolean(false)
//        if (benchmark.buildId in properties) continue
        if (shouldStop) break

        val localPath = benchmark.klass.replace('.', '/') + ".java"
        val srcPath = listOf(
            benchmark.src,
            benchmark.src.resolve("main"),
            benchmark.src.resolve("main", "java"),
            benchmark.src.resolve("java")
        ).filter { it.exists() }.firstOrNull { it.resolve(localPath).exists() }
        if (srcPath == null) {
            log.error("Could not resolve class ${benchmark.klass} in ${benchmark.src}")
            continue
        }
        val props = properties.getOrDefault(benchmark.buildId, Properties(benchmark.buildId, emptyMap()))
        properties[benchmark.buildId] = props.copy(properties = props.properties.toMutableMap().also {
            it["SLoC"] = srcPath.resolve(localPath).readLines().filter { it.isNotBlank() }.size.toString()
        })
        properties[benchmark.buildId] = properties[benchmark.buildId]!!.copy(properties = properties[benchmark.buildId]!!.properties)

        val frame = JFrame(benchmark.buildId)
        frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        val container = frame.contentPane

        val largeTextArea = JTextArea()
        largeTextArea.font = largeTextArea.font.deriveFont(32f)
        largeTextArea.text = srcPath.resolve(localPath).readLines().withIndex().joinToString("\n") {
            "${it.index + 1 } ${it.value}"
        }
        largeTextArea.isEditable = false
        val scrollPane = JScrollPane(largeTextArea)
        scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS

        val rightPanel = JPanel()
        rightPanel.layout = BoxLayout(rightPanel, BoxLayout.Y_AXIS)

        val metrics = metricsProvider.getMetrics(benchmark)
        val largeTextArea2 = JTextArea()
        largeTextArea2.font = largeTextArea.font.deriveFont(32f)
        largeTextArea2.text = metrics.methods.joinToString("\n\n") {
            buildString {
                appendLine(it.methodId)
                for ((branch, type) in it.branches) {
                    appendLine("  ${branch.line.lineNumber} -> $type")
                }
            }
        }
        largeTextArea2.isEditable = false
        val scrollPane2 = JScrollPane(largeTextArea2)
        scrollPane2.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS

        rightPanel.add(JButton("Next").also {
            it.font = it.font.deriveFont(32f)
            val buttonAction = object : AbstractAction(it.text) {
                override fun actionPerformed(evt: ActionEvent) {
                    canContinue.set(true)
                    frame.dispose()
                }
            }
            it.action = buttonAction

            buttonAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_E)
            it.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK), it.text
            )
            it.actionMap.put(it.text, buttonAction)
        })

        rightPanel.add(scrollPane2)
        rightPanel.add(JButton("Stop").also {
            it.font = it.font.deriveFont(32f)
            it.addActionListener {
                canContinue.set(true)
                shouldStop = true
                frame.dispose()
            }
        })

        container.layout = BoxLayout(container, BoxLayout.X_AXIS)
        container.add(scrollPane)
        container.add(rightPanel)
        frame.pack()

        frame.isVisible = true

        while (!canContinue.get()) {
        }
        propertiesFile.bufferedWriter().use { writer ->
            writer.write(getJsonSerializer(pretty = true).encodeToString(properties.values.toList()))
        }
    }
}
