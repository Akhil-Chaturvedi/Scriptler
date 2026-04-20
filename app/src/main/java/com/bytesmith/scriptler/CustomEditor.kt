package com.bytesmith.scriptler

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.widget.NestedScrollView

class CustomEditor(context: Context) : NestedScrollView(context) {

    private val editor: EditText
    private val buttonContainer: LinearLayout
    private val ctx: Context = context

    init {
        editor = EditText(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            minLines = 10
            textSize = 14f
            setTypeface(android.graphics.Typeface.MONOSPACE)
            setTextColor(android.graphics.Color.parseColor("#d4d4d4"))
            setBackgroundColor(android.graphics.Color.parseColor("#1e1e1e"))
            setPadding(16, 16, 16, 16)
            gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            setHorizontallyScrolling(true)
        }

        buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val horizontalLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(editor)
            addView(buttonContainer)
        }

        addView(horizontalLayout)

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateCopyButtons()
            }
        })
    }

    private fun updateCopyButtons() {
        buttonContainer.removeAllViews()
        val lines = editor.text.toString().split("\n")
        for (i in lines.indices) {
            val copyButton = Button(ctx).apply {
                text = "📋"
                textSize = 10f
                setPadding(4, 0, 4, 0)
                setOnClickListener {
                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Script Line", lines[i])
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(ctx, "Line ${i + 1} copied", Toast.LENGTH_SHORT).show()
                    if (i < lines.size - 1) {
                    val nextLinePos = editor.layout.getLineTop(i + 1)
                    smoothScrollTo(0, nextLinePos - this@apply.height)
                    }
                }
            }
            buttonContainer.addView(copyButton)
        }
    }

    fun getText(): String = editor.text.toString()

    fun setText(text: String) {
        editor.setText(text)
        updateCopyButtons()
    }

    fun addTextChangedListener(watcher: TextWatcher) {
        editor.addTextChangedListener(watcher)
    }

    fun setFontSize(size: Int) {
        editor.textSize = size.toFloat()
    }

    fun getView(): View = this
}
