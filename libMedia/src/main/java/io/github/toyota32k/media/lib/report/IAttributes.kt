package io.github.toyota32k.media.lib.report

interface IAttributes {
    data class KeyValue(val name:String, val value:String)

    val title:String
    val subAttributes: List<IAttributes?>
    fun toList(): List<KeyValue>
    fun format(builder:StringBuilder, baseIndent:Int, incrementIndent:Int, itemPrefix: String, attrFormatter:(KeyValue)->String) : StringBuilder {
        val p = " ".repeat(baseIndent)
        builder.append(p).appendLine(title)
        toList().fold(builder) { sb, e ->
            sb.appendLine("$p$itemPrefix${attrFormatter(e)}")
        }
        subAttributes.forEach {
            it?.format(builder, baseIndent+incrementIndent, incrementIndent, itemPrefix, attrFormatter)
        }
        return builder
    }
    fun format(builder: StringBuilder, prefix: String): StringBuilder {
        return format(builder, 0, 2, prefix) { "${it.name} = ${it.value}" }
    }
}