package com.stockbook.app.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Which border treatment a field wears. */
enum class FieldEmphasis { NONE, SELLING_PRICE, CHANGED }

/** When a required-but-empty field starts wearing its accent border. */
enum class RequiredMarking {
    /** Right away. Right for a handful of fields asked at once. */
    IMMEDIATE,

    /**
     * Only after the field has been visited and left empty. Setup step 3 shows
     * three boxes per product; marking them all on arrival reads as a dozen
     * errors before the owner has done anything wrong.
     */
    AFTER_TOUCH
}

/**
 * A text field in the Nocturne style.
 *
 * `BasicTextField` rather than Material's: the design draws its own box, border
 * and placeholder, and Material's `TextField` brings a label, an indicator and a
 * minimum height that all have to be fought off first.
 *
 * Validation in this app is never a toast and never red text — a
 * required-but-empty input carries an accent border, and the primary action goes
 * disabled with a label saying what is missing.
 */
@Composable
fun NocturneField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    height: Dp = Metrics.inputHeight,
    numeric: Boolean = false,
    isRequiredAndEmpty: Boolean = false,
    requiredMarking: RequiredMarking = RequiredMarking.IMMEDIATE,
    emphasis: FieldEmphasis = FieldEmphasis.NONE,
    textAlign: TextAlign = TextAlign.Start,
    prefix: String? = null,
    fontSize: Double = 14.0,
    /**
     * The IME's own key. `Next` moves to the following field and `Done` closes
     * the keyboard — which is why this app needs no toolbar above it, unlike its
     * iOS twin where a numeric keypad has no return key at all.
     */
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    var hasBeenFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val borderColour = when {
        isRequiredAndEmpty &&
            (requiredMarking == RequiredMarking.IMMEDIATE || hasBeenFocused) -> Nocturne.accent
        emphasis == FieldEmphasis.CHANGED -> Nocturne.accent
        emphasis == FieldEmphasis.SELLING_PRICE -> Nocturne.accent700
        focused -> Nocturne.accent
        else -> Nocturne.neutral800
    }
    val animatedBorder by animateColorAsState(borderColour, label = "fieldBorder")

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                label,
                style = NocturneType.fieldLabel,
                color = Nocturne.neutral500,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(Metrics.controlRadius))
                .background(Nocturne.bg)
                .hairline(animatedBorder, Metrics.controlRadius)
                .padding(horizontal = 10.dp)
        ) {
            if (prefix != null) {
                Text(prefix, style = NocturneType.inter(fontSize), color = Nocturne.neutral500)
                Spacer(Modifier.width(6.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = NocturneType.inter(fontSize),
                        color = Nocturne.neutral500,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = textAlign
                    )
                }
                CompositionLocalProvider(
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = Nocturne.accent,
                        backgroundColor = Nocturne.accent.copy(alpha = 0.3f)
                    )
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = NocturneType.inter(fontSize).copy(
                            color = if (emphasis == FieldEmphasis.CHANGED) Nocturne.accent300 else Nocturne.text,
                            textAlign = textAlign
                        ),
                        cursorBrush = SolidColor(Nocturne.accent),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
                            imeAction = imeAction
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Next) },
                            onDone = {
                                keyboard?.hide()
                                focusManager.clearFocus()
                                onImeAction?.invoke()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused) hasBeenFocused = true
                            }
                    )
                }
            }
        }
    }
}
