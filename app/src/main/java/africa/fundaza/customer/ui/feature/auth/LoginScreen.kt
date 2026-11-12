package africa.fundaza.customer.ui.feature.auth

import africa.fundaza.customer.ui.components.Banner
import africa.fundaza.customer.ui.components.BannerTone
import africa.fundaza.customer.ui.components.PrimaryButton
import africa.fundaza.customer.ui.components.SecondaryButton
import africa.fundaza.customer.ui.theme.Bricolage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val ForestDeep = Color(0xFF0B2A21)
private val Forest = Color(0xFF0E3B2E)
private val MarigoldC = Color(0xFFF2A93B)
private val PaperC = Color(0xFFF6F1E7)

@Composable
fun LoginScreen(notice: String?, onRegister: () -> Unit, viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    var showPassword by rememberSaveable { mutableStateOf(false) }

    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(700)) }

    Box(Modifier.fillMaxSize().background(Forest)) {
        Routes(Modifier.fillMaxWidth().height(340.dp))

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 28.dp)
                    .padding(top = 56.dp, bottom = 36.dp)
                    .graphicsLayer {
                        alpha = reveal.value
                        translationY = (1f - reveal.value) * 40f
                    },
            ) {
                Text(
                    "fundaza",
                    color = MarigoldC,
                    fontFamily = Bricolage,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.height(40.dp))
                Text(
                    buildAnnotatedString {
                        append("Send it home.\n")
                        withStyle(SpanStyle(color = MarigoldC)) { append("Watch it land.") }
                    },
                    style = MaterialTheme.typography.displayMedium,
                    color = PaperC,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Transfers across borders, with every fee shown before you pay.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PaperC.copy(alpha = 0.72f),
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 560.dp),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                        .navigationBarsPadding(),
                ) {
                    Text("Sign in", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(20.dp))

                    if (notice != null && state.formError == null) {
                        Banner(notice, tone = BannerTone.Warning, icon = Icons.Outlined.Info)
                        Spacer(Modifier.height(16.dp))
                    }

                    OutlinedTextField(
                        value = state.email,
                        onValueChange = viewModel::onEmailChange,
                        label = { Text("Email address") },
                        singleLine = true,
                        isError = state.emailError != null,
                        supportingText = state.emailError?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text("Password") },
                        singleLine = true,
                        isError = state.passwordError != null,
                        supportingText = state.passwordError?.let { { Text(it) } },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focus.clearFocus()
                            viewModel.submit()
                        }),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    AnimatedVisibility(state.formError != null, enter = fadeIn() + expandVertically()) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            Banner(state.formError.orEmpty(), tone = BannerTone.Error, icon = Icons.Outlined.ErrorOutline)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    PrimaryButton(
                        text = "Sign in",
                        loading = state.submitting,
                        onClick = {
                            focus.clearFocus()
                            viewModel.submit()
                        },
                    )
                    Spacer(Modifier.height(20.dp))
                    SecondaryButton(text = "New to Fundaza? Create an account", onClick = onRegister)
                }
            }
        }
    }
}

/** Arcs between dots — money crossing borders — drawn behind the headline. */
@Composable
private fun Routes(modifier: Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1400)) }
    Canvas(modifier.background(ForestDeep.copy(alpha = 0.0f))) {
        val w = size.width
        val h = size.height
        val arcs = listOf(
            Triple(Offset(w * 0.12f, h * 0.88f), Offset(w * 0.92f, h * 0.30f), 0.45f),
            Triple(Offset(w * 0.30f, h * 0.95f), Offset(w * 0.78f, h * 0.10f), 0.30f),
            Triple(Offset(w * 0.55f, h * 0.92f), Offset(w * 1.05f, h * 0.55f), 0.20f),
        )
        arcs.forEach { (from, to, alpha) ->
            val control = Offset((from.x + to.x) / 2f, minOf(from.y, to.y) - h * 0.25f)
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(from.x, from.y)
                quadraticTo(control.x, control.y, to.x, to.y)
            }
            val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
            val partial = androidx.compose.ui.graphics.Path()
            measure.getSegment(0f, measure.length * progress.value, partial, true)
            drawPath(
                partial,
                color = MarigoldC.copy(alpha = alpha),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 12f))),
            )
            drawCircle(MarigoldC.copy(alpha = alpha + 0.2f), radius = 4.dp.toPx(), center = from)
            if (progress.value > 0.98f) drawCircle(MarigoldC, radius = 5.dp.toPx(), center = to, alpha = alpha + 0.3f)
        }
    }
}
