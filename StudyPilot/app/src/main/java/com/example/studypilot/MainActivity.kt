package com.example.studypilot
import android.os.Bundle
import android.os.Build
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.studypilot.data.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import java.util.Locale
import java.util.Random

private val Navy = Color(0xFF173B70)
private val Blue = Color(0xFF2878D0)
private val PaleBlue = Color(0xFFEAF3FF)
private val Page = Color(0xFFF6F8FC)
private val Green = Color(0xFF249B70)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = StudyPilotDatabase.getDatabase(applicationContext)
        val repository = StudyTaskRepository(database.studyTaskDao())
        val userRepository = UserRepository(database.userDao())
        val factory = StudyPilotViewModelFactory(repository, userRepository)

        setContent {
            val vm: StudyPilotViewModel = viewModel(factory = factory)
            StudyPilotTheme {
                val currentUser by vm.currentUser.collectAsState()
                if (currentUser == null) {
                    AuthRoot(vm)
                } else {
                    StudyPilotApp(vm)
                }
            }
        }
    }
}

@Composable
fun StudyPilotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = Color(0xFF72D6B0),
            secondary = Blue,
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E)
        )
        else -> lightColorScheme(
            primary = Navy,
            secondary = Blue,
            background = Page,
            surface = Color.White
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
fun AuthRoot(vm: StudyPilotViewModel) {
    val nav = rememberNavController()
    val authError by vm.authError.collectAsState()
    val context = LocalContext.current

    NavHost(navController = nav, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLogin = { email, pass ->
                    vm.login(email, pass) {
                        Toast.makeText(context, "Login successful", Toast.LENGTH_SHORT).show()
                    }
                },
                onNavigateToRegister = { nav.navigate("register") },
                errorMessage = authError
            )
        }
        composable("register") {
            RegisterScreen(
                onRegister = { username, email, pass ->
                    vm.register(username, email, pass) {
                        Toast.makeText(context, "Account created successfully", Toast.LENGTH_SHORT).show()
                    }
                },
                onNavigateToLogin = { nav.popBackStack() },
                errorMessage = authError
            )
        }
    }
}

@Composable
fun StudyPilotApp(vm: StudyPilotViewModel) {
    val nav = rememberNavController()
    val tasks by vm.tasks.collectAsState()
    val context = LocalContext.current
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "home"

    Scaffold(
        containerColor = Page,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val tabs = listOf(
                    Triple("home", "Home", Icons.Default.Home),
                    Triple("planner", "Planner", Icons.Default.CalendarMonth),
                    Triple("practice", "Practice", Icons.Default.Quiz),
                    Triple("progress", "Progress", Icons.Default.Insights),
                    Triple("materials", "Materials", Icons.AutoMirrored.Filled.MenuBook)
                )
                tabs.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = current == route,
                        onClick = {
                            nav.navigate(route) {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                val currentUser by vm.currentUser.collectAsState()
                HomeScreen(
                    tasks = tasks,
                    username = currentUser?.username ?: "Student",
                    onPlanner = { nav.navigate("planner") },
                    onPractice = { nav.navigate("practice") },
                    onPomodoro = { nav.navigate("pomodoro") },
                    onFlashcards = { nav.navigate("flashcards") },
                    onTask = { nav.navigate("detail/$it") },
                    onLogout = { vm.logout() }
                )
            }
            composable("planner") {
                PlannerScreen(tasks, onAdd = { nav.navigate("add") },
                    onTask = { nav.navigate("detail/$it") })
            }
            composable("add") {
                AddTaskScreen(onSave = { subject, topic, minutes, priority ->
                    vm.addTask(subject, topic, minutes, priority) {
                        Toast.makeText(context, "Study task saved", Toast.LENGTH_SHORT).show()
                        nav.popBackStack()
                    }
                }, onCancel = { nav.popBackStack() })
            }
            composable("detail/{taskId}", arguments = listOf(navArgument("taskId") { type = NavType.IntType })) { entry ->
                val id = entry.arguments?.getInt("taskId") ?: -1
                val task = tasks.firstOrNull { it.id == id }
                if (task == null) {
                    EmptyState("Task not found", "It may have been deleted.")
                } else {
                    TaskDetailScreen(task,
                        onBack = { nav.popBackStack() },
                        onComplete = { vm.toggleCompleted(task); Toast.makeText(context, "Task updated", Toast.LENGTH_SHORT).show() },
                        onDelete = {
                            vm.deleteTask(task) {
                                Toast.makeText(context, "Task deleted", Toast.LENGTH_SHORT).show()
                                nav.popBackStack()
                            }
                        },
                        onPractice = { subject, topic -> nav.navigate("practice/$subject?topic=$topic") },
                        onOpenMaterial = { subject -> nav.navigate("materials?subject=$subject") }
                    )
                }
            }
            composable(
                "practice/{subject}?topic={topic}",
                arguments = listOf(
                    navArgument("subject") { defaultValue = "" },
                    navArgument("topic") { defaultValue = "" }
                )
            ) { entry ->
                val subject = entry.arguments?.getString("subject") ?: ""
                val topic = entry.arguments?.getString("topic") ?: ""
                PracticeScreen(
                    tasks = tasks,
                    initialSubject = subject,
                    initialTopic = topic,
                    onMarkCompleted = { subj ->
                        tasks.filter { it.subject.equals(subj, ignoreCase = true) && !it.completed }.forEach { task ->
                            vm.setCompleted(task, true)
                        }
                    }
                )
            }
            composable("practice") {
                PracticeScreen(
                    tasks = tasks,
                    initialSubject = "",
                    initialTopic = "",
                    onMarkCompleted = { subj ->
                        tasks.filter { it.subject.equals(subj, ignoreCase = true) && !it.completed }.forEach { task ->
                            vm.setCompleted(task, true)
                        }
                    }
                )
            }
            composable("progress") { ProgressScreen(tasks) }
            composable(
                "materials?subject={subject}",
                arguments = listOf(navArgument("subject") { defaultValue = "" })
            ) { entry ->
                val subject = entry.arguments?.getString("subject") ?: ""
                var selectedMaterial by remember(subject) { mutableStateOf<StudyMaterial?>(null) }

                val dynamicMats = tasks.map { task ->
                    StudyMaterial(
                        title = task.subject,
                        subtitle = "Topic: ${task.topic} • Offline study notes",
                        content = "Comprehensive study notes and revision guide for ${task.subject}, specifically focusing on '${task.topic}'. Review these core concepts, definitions, and problem-solving strategies to ensure thorough preparation.",
                        keyPoints = listOf(
                            "Fundamental definitions and core theory of ${task.topic}",
                            "Practical applications and methodologies in ${task.subject}",
                            "Key terminology, formulas, and critical problem-solving steps",
                            "Self-assessment and exam preparation guidelines"
                        )
                    )
                }.distinctBy { it.title }
                val allMats = (dynamicMats + sampleMaterials).distinctBy { it.title }

                if (subject.isNotBlank()) {
                    val directMat = allMats.find { it.title.equals(subject, ignoreCase = true) }
                        ?: StudyMaterial(
                            title = subject,
                            subtitle = "Topic: Core Revision • Offline study notes",
                            content = "Comprehensive study notes and revision guide for $subject. Review these core concepts, definitions, and problem-solving strategies.",
                            keyPoints = listOf(
                                "Fundamental definitions and core theory of $subject",
                                "Practical applications and methodologies",
                                "Key terminology and critical problem-solving steps",
                                "Self-assessment guidelines"
                            )
                        )
                    MaterialReaderScreen(
                        material = directMat,
                        onBack = { nav.popBackStack() },
                        onTakeQuiz = { subj, top -> nav.navigate("practice/$subj?topic=$top") }
                    )
                } else {
                    if (selectedMaterial == null) {
                        MaterialsScreen(tasks, onSelect = { selectedMaterial = it })
                    } else {
                        MaterialReaderScreen(
                            material = selectedMaterial!!,
                            onBack = { selectedMaterial = null },
                            onTakeQuiz = { subj, top -> nav.navigate("practice/$subj?topic=$top") }
                        )
                    }
                }
            }
            composable("pomodoro") { PomodoroScreen(onBack = { nav.popBackStack() }) }
            composable("flashcards") { FlashcardsScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Navy)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
fun HomeScreen(
    tasks: List<StudyTask>,
    username: String,
    onPlanner: () -> Unit,
    onPractice: () -> Unit,
    onPomodoro: () -> Unit,
    onFlashcards: () -> Unit,
    onTask: (Int) -> Unit,
    onLogout: () -> Unit
) {
    val completed = tasks.count { it.completed }
    val percent = if (tasks.isEmpty()) 0 else completed * 100 / tasks.size
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("STUDYPILOT", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    TextButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Logout", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                Text("Good day, $username! 👋", color = Navy, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Plan smarter. Study offline. Improve faster.", color = Color.Gray, fontSize = 13.sp)
                Spacer(Modifier.height(18.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("YOUR DAILY PROGRESS", color = Color.White.copy(alpha = .75f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$percent%", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text("$completed of ${tasks.size} tasks completed", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = Color(0xFF72D6B0),
                            trackColor = Color.White.copy(alpha = .2f)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Study tasks", tasks.size.toString(), Icons.Default.TaskAlt, Modifier.weight(1f))
                    StatCard("Completed", completed.toString(), Icons.Default.CheckCircle, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                val xp = completed * 50 + tasks.size * 25
                val level = (xp / 150) + 1
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).background(PaleBlue, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.EmojiEvents, null, tint = Blue, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Level $level Scholar", color = Navy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("$xp Total XP • 3-Day Streak 🔥", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Today's study plan", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Navy)
                    TextButton(onClick = onPlanner) { Text("View all") }
                }
            }
        }
        if (tasks.isEmpty()) {
            item { EmptyState("Your study plan is empty", "Add your first task to get started.") }
        } else {
            items(tasks.take(4), key = { it.id }) { task ->
                TaskCard(task) { onTask(task.id) }
            }
        }
        item {
            Spacer(Modifier.height(14.dp))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onPlanner, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Plan study")
                    }
                    OutlinedButton(onClick = onPractice, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Bolt, null); Spacer(Modifier.width(5.dp)); Text("Practice")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onPomodoro, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Timer, null); Spacer(Modifier.width(5.dp)); Text("Focus Timer")
                    }
                    OutlinedButton(onClick = onFlashcards, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Style, null); Spacer(Modifier.width(5.dp)); Text("Flashcards")
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = Blue)
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text(label, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun PlannerScreen(tasks: List<StudyTask>, onAdd: () -> Unit, onTask: (Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ScreenTitle("Study Planner", "Organise your subjects and study sessions")
            }
            FilledTonalIconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        }
        if (tasks.isEmpty()) {
            EmptyState("No study tasks yet", "Tap + to create your first study session.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
                items(tasks, key = { it.id }) { task -> TaskCard(task) { onTask(task.id) } }
            }
        }
    }
}

@Composable
fun TaskCard(task: StudyTask, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(PaleBlue, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(if (task.completed) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.MenuBook, null, tint = Blue)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(task.subject, fontWeight = FontWeight.Bold, color = Navy)
                Text(task.topic, color = Color.Gray, fontSize = 13.sp)
                Text("${task.durationMinutes} min • ${task.priority} priority", color = Blue, fontSize = 11.sp)
            }
            if (task.completed) Icon(Icons.Default.CheckCircle, null, tint = Green)
            else Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
        }
    }
}



                        score = 0
                        quizFinished = false
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy)
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retake Quiz", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("SUBJECT: $selectedSubject".uppercase(), color = Blue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("QUESTION ${index + 1} OF ${questions.size}", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(question.questionText, color = Navy, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(18.dp))
                    question.shuffledOptions.forEach { option ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { if (selected == null) selected = option },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = when {
                                    selected == option && option == question.correctAnswer -> Color(0xFFE5F7EF)
                                    selected == option -> Color(0xFFFFEEEE)
                                    else -> Color.White
                                }
                            )
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected == option, onClick = { if (selected == null) selected = option })
                                Spacer(Modifier.width(8.dp))
                                Text(option, color = Navy)
                            }
                        }
                    }
                    if (selected != null) {
                        Spacer(Modifier.height(12.dp))
                        val correct = selected == question.correctAnswer
                        Text(
                            if (correct) "Correct! Great work." else "Correct answer: ${question.correctAnswer}",
                            color = if (correct) Green else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Score: $score / ${questions.size}", color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            }

            if (selected != null) {
                Spacer(Modifier.height(16.dp))
                val correct = selected == question.correctAnswer
                Button(
                    onClick = {
                        if (correct) score++
                        if (index < questions.lastIndex) {
                            index++
                            selected = null
                        } else {
                            quizFinished = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(if (index == questions.lastIndex) "Submit Quiz" else "Next question", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun ProgressScreen(tasks: List<StudyTask>) {
    val completed = tasks.count { it.completed }
    val percent = if (tasks.isEmpty()) 0 else completed * 100 / tasks.size
    val xp = completed * 50 + tasks.size * 25
    val level = (xp / 150) + 1

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        ScreenTitle("Your Progress & Analytics", "Track your learning journey and achievements")
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("OVERALL COMPLETION", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("$percent%", color = Navy, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().height(10.dp))
                Spacer(Modifier.height(12.dp))
                Text("$completed completed out of ${tasks.size} total tasks", color = Color.Gray)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Level $level", "$xp XP", Icons.Default.EmojiEvents, Modifier.weight(1f))
            StatCard("Streak", "3 Days 🔥", Icons.Default.LocalFireDepartment, Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        Text("Unlocked Badges", fontSize = 18.sp, color = Navy, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BadgeChip("🌱 Beginner", true)
            BadgeChip("🔥 Streak", true)
            BadgeChip("⭐ Achiever", completed > 0)
            BadgeChip("⚡ Quiz Master", true)
        }
        Spacer(Modifier.height(20.dp))
        val subjects = tasks.groupBy { it.subject }
        Text("Subject Mastery Chart", fontSize = 18.sp, color = Navy, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                if (subjects.isEmpty()) {
                    Text("No subjects yet. Add tasks to view chart.", color = Color.Gray, fontSize = 13.sp)
                } else {
                    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                        val barWidth = size.width / (subjects.size * 2f).coerceAtLeast(1f)
                        var x = barWidth / 2f
                        subjects.forEach { (_, list) ->
                            val ratio = list.count { it.completed }.toFloat() / list.size.coerceAtLeast(1)
                            val barHeight = size.height * ratio
                            drawRect(
                                color = Blue,
                                topLeft = Offset(x, size.height - barHeight),
                                size = Size(barWidth, barHeight.coerceAtLeast(4f))
                            )
                            x += barWidth * 2f
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Subject overview", fontSize = 18.sp, color = Navy, fontWeight = FontWeight.Bold)
        if (subjects.isEmpty()) Text("Add study tasks to see your subject progress.", color = Color.Gray, modifier = Modifier.padding(top = 12.dp))
        subjects.forEach { (subject, list) ->
            val done = list.count { it.completed }
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(subject, fontWeight = FontWeight.SemiBold, color = Navy)
                    Text("$done/${list.size}", color = Color.Gray)
                }
                LinearProgressIndicator(progress = { done.toFloat() / list.size }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
    }
}

@Composable
fun BadgeChip(text: String, unlocked: Boolean) {
    Surface(
        color = if (unlocked) PaleBlue else Color.LightGray.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (unlocked) Navy else Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

data class StudyMaterial(
    val title: String,
    val subtitle: String,
    val content: String,
    val keyPoints: List<String>
)

val sampleMaterials = listOf(
    StudyMaterial(
        "Database Management Systems",
        "Revision notes • Offline sample",
        "A Database Management System (DBMS) is software designed to store, retrieve, query, and manage data securely in a database. Key concepts include ACID properties, normalization (1NF, 2NF, 3NF, BCNF), indexing (clustered and non-clustered), and SQL joins.",
        listOf(
            "Atomicity, Consistency, Isolation, Durability (ACID)",
            "Normalization eliminates redundancy and update anomalies",
            "SQL Joins: INNER, LEFT, RIGHT, FULL OUTER",
            "Indexes speed up data retrieval at the cost of write performance"
        )
    ),
    StudyMaterial(
        "Data Structures and Algorithms",
        "Quick reference • Offline sample",
        "Data structures are specialized formats for organizing and storing data efficiently. Algorithms are step-by-step procedures for solving computational problems. Understanding time and space complexity (Big O notation) is essential.",
        listOf(
            "Linear structures: Arrays, Linked Lists, Stacks, Queues",
            "Non-linear structures: Trees, Graphs, Heaps",
            "Sorting algorithms: Merge Sort, Quicksort, Bubble Sort",
            "Graph traversals: Breadth-First Search (BFS) and Depth-First Search (DFS)"
        )
    ),
    StudyMaterial(
        "Programming Fundamentals",
        "Practice guide • Offline sample",
        "Core principles of object-oriented programming (OOP) and software design. Emphasizes clean code, reusability, and modular architecture.",
        listOf(
            "Four pillars of OOP: Encapsulation, Inheritance, Polymorphism, Abstraction",
            "SOLID principles for maintainable software architecture",
            "Control flow: conditionals, loops, and recursion",
            "Error handling and exception management"
        )
    ),
    StudyMaterial(
        "Exam Preparation Checklist",
        "Study checklist • Offline sample",
        "A strategic guide to preparing effectively for examinations, managing time, and retaining complex concepts.",
        listOf(
            "Create a structured study schedule using StudyPilot planner",
            "Practice active recall with flashcards and practice quizzes",
            "Use Pomodoro 25-minute focus sessions to prevent burnout",
            "Review past exam papers and summarize key formulas"
        )
    )
)

@Composable
fun MaterialsScreen(tasks: List<StudyTask>, onSelect: (StudyMaterial) -> Unit) {
    val dynamicMaterials = tasks.map { task ->
        StudyMaterial(
            title = task.subject,
            subtitle = "Topic: ${task.topic} • Offline study notes",
            content = "Comprehensive study notes and revision guide for ${task.subject}, specifically focusing on '${task.topic}'. Review these core concepts, definitions, and problem-solving strategies to ensure thorough preparation.",
            keyPoints = listOf(
                "Fundamental definitions and core theory of ${task.topic}",
                "Practical applications and methodologies in ${task.subject}",
                "Key terminology, formulas, and critical problem-solving steps",
                "Self-assessment and exam preparation guidelines"
            )
        )
    }.distinctBy { it.title }

    val allMaterials = (dynamicMaterials + sampleMaterials).distinctBy { it.title }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Study Materials", "Offline-friendly learning library tailored to your subjects.")
        LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
            items(allMaterials) { material ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clickable { onSelect(material) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(PaleBlue, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PictureAsPdf, null, tint = Blue)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(material.title, color = Navy, fontWeight = FontWeight.Bold)
                            Text(material.subtitle, color = Color.Gray, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun MaterialReaderScreen(
    material: StudyMaterial,
    onBack: () -> Unit,
    onTakeQuiz: (String, String) -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val context = LocalContext.current

    DisposableEffect(context) {
        val t = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
        tts = t
        onDispose {
            t.stop()
            t.shutdown()
        }
    }

    val subj = material.title
    val topic = material.subtitle.substringAfter("Topic: ").substringBefore(" •")

    val pages = listOf(
        Triple(
            "Chapter 1: Introduction & Foundational Overview",
            "Welcome to Chapter 1 of the comprehensive study guide for $subj, focusing specifically on '$topic'. This introductory section establishes the core definitions, background context, and preliminary terminology essential for building a robust mental framework. Understanding these initial principles provides the necessary springboard for advanced analytical reasoning and deep conceptual mastery.",
            listOf(
                "Core definition and historical background of $topic",
                "Primary terminology and foundational vocabulary in $subj",
                "Identifying the scope and objectives of studying $topic",
                "Initial conceptual mapping and framework setup"
            )
        ),
        Triple(
            "Chapter 2: Core Architecture & Theoretical Framework",
            "In Chapter 2, we dive deeper into the underlying architecture and theoretical models supporting $topic. Here we examine how abstract concepts are structured, categorized, and modeled to form a cohesive foundation for practical implementation in $subj.",
            listOf(
                "High-level structural models and abstractions",
                "Theoretical assumptions and boundary definitions",
                "Relationship between core theory and practice",
                "Taxonomy and classification of primary elements"
            )
        ),
        Triple(
            "Chapter 3: Key Mechanisms & Operational Workflows",
            "Chapter 3 explores the detailed operational mechanics and step-by-step workflows of $topic. We analyze how individual sub-components interact, how systematic processes are regulated, and the exact sequence of events during execution.",
            listOf(
                "Detailed operational mechanics and workflow sequencing",
                "Interdependent relationships between key sub-components",
                "State transitions and progression milestones",
                "Monitoring and validation checkpoints"
            )
        ),
        Triple(
            "Chapter 4: Advanced Principles & Governing Laws",
            "Chapter 4 investigates the advanced governing laws, mathematical or logical rules, and axiomatic constraints that dictate behavior within $topic. Mastery of these rules is vital for predicting system outcomes under diverse conditions.",
            listOf(
                "Axiomatic rules and fundamental governing equations",
                "Constraint satisfaction and boundary limitations",
                "Predictive modeling and scenario analysis",
                "Advanced theoretical theorems and proofs"
            )
        ),
        Triple(
            "Chapter 5: Common Challenges & Edge Cases",
            "Even experienced students encounter pitfalls when studying $topic. Chapter 5 highlights frequent misconceptions, complex edge cases, concurrency hurdles, and anti-patterns to avoid.",
            listOf(
                "Identifying subtle logical flaws and misconceptions",
                "Handling complex edge cases and failure modes",
                "Recognizing harmful anti-patterns in $subj",
                "Preventing unexpected errors and bottlenecks"
            )
        ),
        Triple(
            "Chapter 6: Analytical Methodologies & Problem Solving",
            "In Chapter 6, we explore structured methodologies for breaking down and solving complex problems related to $topic. You will learn systematic debugging, decomposition, and analytical evaluation techniques.",
            listOf(
                "Problem decomposition and step-by-step reduction",
                "Systematic debugging and root-cause analysis",
                "Evaluating competing solutions objectively",
                "Algorithmic and logical problem-solving strategies"
            )
        ),
        Triple(
            "Chapter 7: Real-World Case Studies & Practical Applications",
            "Theory comes alive in Chapter 7 through real-world case studies demonstrating how $topic is successfully applied across industry and research within $subj. We examine practical deployments and success stories.",
            listOf(
                "Real-world deployment scenarios and case studies",
                "Translating theoretical concepts into production code or practice",
                "Industry standards and benchmarks",
                "Scalability and performance considerations"
            )
        ),
        Triple(
            "Chapter 8: Best Practices & Design Standards",
            "Chapter 8 outlines professional best practices, design patterns, and coding or writing standards recommended when working with $topic. Adhering to these standards ensures maintainability and elegance.",
            listOf(
                "Industry-accepted design patterns and paradigms",
                "Clean code, modularity, and reusability standards",
                "Documentation and maintainability guidelines",
                "Optimization and efficiency best practices"
            )
        ),
        Triple(
            "Chapter 9: Self-Assessment, Review & Active Recall",
            "As we approach the end of our guide, Chapter 9 focuses on active recall techniques, self-assessment questions, and flashcard-style reinforcement to ensure long-term memory consolidation for $topic.",
            listOf(
                "Active recall questioning and mental rehearsal",
                "Self-assessment quizzes and knowledge checks",
                "Spaced repetition revision strategies",
                "Synthesizing key formulas, definitions, and rules"
            )
        ),
        Triple(
            "Chapter 10: Summary, Exam Checklist & Final Synthesis",
            "Our final chapter provides a comprehensive revision summary and exam preparation checklist for $topic in $subj. Review these final takeaways, complete your practice quizzes, and prepare to excel in your examinations!",
            listOf(
                "Executive summary of all core concepts in $topic",
                "Final exam preparation checklist and time management tips",
                "Transitioning from study notes to practice quizzes",
                "Congratulations on completing 10 chapters of study!"
            )
        )
    )

    val page = pages[currentPage]

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Text(" Library") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    tts?.speak(page.second, TextToSpeech.QUEUE_FLUSH, null, null)
                }) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Listen to audio notes", tint = Blue)
                }
                Text("Page ${currentPage + 1} of ${pages.size}", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("STUDY NOTES", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text(material.title, color = Navy, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(page.first, color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Chapter Content", color = Navy, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(page.second, color = Color.DarkGray, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(20.dp))
                Text("Key Takeaways", color = Navy, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                page.third.forEach { point ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.CheckCircle, null, tint = Green, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(point, color = Color.DarkGray, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = { if (currentPage > 0) currentPage-- },
                enabled = currentPage > 0,
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text("Previous Page")
            }
            Button(
                onClick = {
                    if (currentPage < pages.lastIndex) {
                        currentPage++
                    } else {
                        onTakeQuiz(subj, topic)
                    }
                },
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text(if (currentPage < pages.lastIndex) "Next Page" else "Take Quiz on $topic")
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.AutoStories, null, tint = Blue, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = Navy, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
fun PomodoroScreen(onBack: () -> Unit) {
    var timeLeft by remember { mutableIntStateOf(25 * 60) }
    var isRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning, timeLeft) {
        if (isRunning && timeLeft > 0) {
            delay(1.seconds)
            timeLeft--
        } else if (timeLeft == 0) {
            isRunning = false
        }
    }

    val minutes = timeLeft / 60
    val seconds = timeLeft % 60
    val timeString = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Text(" Back") }
        }
        Spacer(Modifier.height(10.dp))
        ScreenTitle("Focus Timer (Pomodoro)", "Study deeply without distractions")
        Spacer(Modifier.height(40.dp))

        Card(
            modifier = Modifier.size(240.dp),
            shape = RoundedCornerShape(120.dp),
            colors = CardDefaults.cardColors(containerColor = Navy)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(timeString, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(40.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { isRunning = !isRunning },
                modifier = Modifier.width(140.dp).height(50.dp)
            ) {
                Icon(if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isRunning) "Pause" else "Start")
            }
            OutlinedButton(
                onClick = { isRunning = false; timeLeft = 25 * 60 },
                modifier = Modifier.width(140.dp).height(50.dp)
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Reset")
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Tip: Take a 5-minute break after every 25 minutes of focused study.", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
fun FlashcardsScreen(onBack: () -> Unit) {
    val cards = listOf(
        "What is ACID in Databases?" to "Atomicity, Consistency, Isolation, Durability",
        "What is LIFO?" to "Last-In, First-Out (used in Stacks)",
        "What is Dijkstra's Algorithm?" to "Finds the shortest path from a source node to all other nodes in a graph",
        "What is Polymorphism?" to "The ability of an object or function to take on many forms",
        "What is a Deadlock?" to "A state where two or more threads are blocked waiting for each other"
    )
    var currentIndex by remember { mutableIntStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }

    val currentCard = cards[currentIndex]

    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Text(" Back") }
        }
        Spacer(Modifier.height(10.dp))
        ScreenTitle("Study Flashcards", "Quick interactive revision")
        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth().height(260.dp).clickable { showAnswer = !showAnswer },
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp)
        ) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (!showAnswer) "QUESTION (${currentIndex + 1}/${cards.size})" else "ANSWER (${currentIndex + 1}/${cards.size})",
                        color = Blue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (!showAnswer) currentCard.first else currentCard.second,
                        color = Navy,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "(Tap card to flip)",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(30.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = { if (currentIndex > 0) { currentIndex--; showAnswer = false } },
                enabled = currentIndex > 0,
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text("Previous")
            }
            Button(
                onClick = {
                    if (currentIndex < cards.lastIndex) {
                        currentIndex++
                        showAnswer = false
                    } else {
                        currentIndex = 0
                        showAnswer = false
                    }
                },
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text(if (currentIndex < cards.lastIndex) "Next Card" else "Restart")
            }
        }
    }
}
