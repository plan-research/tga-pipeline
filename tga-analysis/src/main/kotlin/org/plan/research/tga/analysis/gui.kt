package org.plan.research.tga.analysis

import kotlinx.serialization.encodeToString
import org.plan.research.tga.core.benchmark.json.JsonBenchmarkProvider
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.resolve
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.nio.file.Paths
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
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

    var shouldStop = false

    for (benchmark in JsonBenchmarkProvider(Paths.get("benchmarks/benchmarks.json")).benchmarks()) {
        if (benchmark.buildId in properties) continue
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
        largeTextArea.text = srcPath.resolve(localPath).readText()
        largeTextArea.isEditable = false
        val scrollPane = JScrollPane(largeTextArea)
        scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS

        val rightPanel = JPanel()
        rightPanel.layout = BoxLayout(rightPanel, BoxLayout.Y_AXIS)

        val fields = mutableMapOf<String, JTextField>()
        var canContinue = false

        for (name in listOf(
            "Internal package",
            "Number of internal dependencies",
            "Number of stdlib dependencies",
            "Number of external dependencies",
            "Language",
            "Comments",
            "Java docs"
        )) {
            val label = JLabel(name)
            label.font = label.font.deriveFont(32f)
            val field = JTextField(
                properties[benchmark.buildId]?.properties?.get(name) ?: if (name == "Language") "Eng" else ""
            )
            field.font = field.font.deriveFont(32f)
            field.maximumSize = Dimension(Int.MAX_VALUE, 100)
            fields[name] = field

            rightPanel.add(label)
            rightPanel.add(field)
        }

        rightPanel.add(JButton("Analyze").also {
            it.font = it.font.deriveFont(32f)
            val buttonAction = object : AbstractAction(it.text) {
                override fun actionPerformed(evt: ActionEvent) {
                    val internalPackage = fields["Internal package"]!!.text
                    val imports = largeTextArea.text.split("\n")
                        .filter { line -> line.startsWith("import") }
                        .map { line -> line.removePrefix("import ").removePrefix("static ") }
                    val internal = imports.count { line -> line.startsWith(internalPackage) }
                    val std =
                        imports.count { line ->
                            line.startsWith("java.") || line.startsWith("javax.") || line.startsWith(
                                "javafx."
                            )
                        }
                    val external = imports.size - internal - std
                    fields["Number of internal dependencies"]!!.text = internal.toString()
                    fields["Number of stdlib dependencies"]!!.text = std.toString()
                    fields["Number of external dependencies"]!!.text = external.toString()
                }
            }
            it.action = buttonAction

            buttonAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_E)
            it.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK), it.text
            )
            it.actionMap.put(it.text, buttonAction)
        })

        rightPanel.add(JButton("Save").also {
            it.font = it.font.deriveFont(32f)

            val buttonAction = object : AbstractAction(it.text) {
                override fun actionPerformed(evt: ActionEvent) {
                    canContinue = true
                    for ((key, value) in fields) {
                        log.debug("Benchmark ${benchmark.buildId}, $key = ${value.text}")
                    }

                    properties[benchmark.buildId] =
                        Properties(benchmark.buildId, fields.mapValues { entry -> entry.value.text }.toMap())

                    propertiesFile.bufferedWriter().use { writer ->
                        writer.write(getJsonSerializer(pretty = true).encodeToString(properties.values.toList()))
                    }

                    frame.dispose()
                }
            }
            it.action = buttonAction

            buttonAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_R)
            it.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), it.text
            )
            it.actionMap.put(it.text, buttonAction)
        })

        rightPanel.add(JButton("Stop").also {
            it.font = it.font.deriveFont(32f)
            it.addActionListener {
                canContinue = true
                shouldStop = true
                frame.dispose()
            }
        })

        container.layout = BoxLayout(container, BoxLayout.X_AXIS)
        container.add(scrollPane)
        container.add(rightPanel)
        frame.pack()

        frame.isVisible = true

        while (!canContinue) {
        }
        propertiesFile.bufferedWriter().use { writer ->
            writer.write(getJsonSerializer(pretty = true).encodeToString(properties.values.toList()))
        }
    }
}
