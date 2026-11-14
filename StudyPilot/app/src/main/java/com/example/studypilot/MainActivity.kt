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


@Composable
fun AddTaskScreen(
    onSave: (String, String, Int, String) -> Unit,
    onCancel: () -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("30") }
    var priority by remember { mutableStateOf("Medium") }
    var error by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Text(" Back") }
        Text("Create study task", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("Add a subject and decide what you want to study.", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(subject, { subject = it; error = "" }, label = { Text("Subject") },
            placeholder = { Text("e.g. Database Management") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(topic, { topic = it; error = "" }, label = { Text("Topic") },
            placeholder = { Text("e.g. SQL Joins") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(duration, { duration = it.filter(Char::isDigit); error = "" },
            label = { Text("Study duration (minutes)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(14.dp))
        Text("Priority", fontWeight = FontWeight.SemiBold, color = Navy)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Low", "Medium", "High").forEach { option ->
                FilterChip(selected = priority == option, onClick = { priority = option }, label = { Text(option) })
            }
        }
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = {
                val mins = duration.toIntOrNull()
                error = when {
                    subject.isBlank() -> "Please enter a subject."
                    topic.isBlank() -> "Please enter a topic."
                    mins == null || mins !in 1..600 -> "Duration must be between 1 and 600 minutes."
                    else -> ""
                }
                if (error.isBlank()) onSave(subject, topic, mins!!, priority)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Save study task") }
    }
}

@Composable
fun TaskDetailScreen(
    task: StudyTask,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onPractice: (String, String) -> Unit,
    onOpenMaterial: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Text(" Back") }
        Spacer(Modifier.height(12.dp))
        Text("TASK DETAILS", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text(task.subject, color = Navy, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(task.topic, color = Color.Gray, fontSize = 16.sp)
        Spacer(Modifier.height(22.dp))
        StatCard("Study duration", "${task.durationMinutes} min", Icons.Default.Timer, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        StatCard("Priority", task.priority, Icons.Default.Flag, Modifier.fillMaxWidth())
        Spacer(Modifier.height(22.dp))
        Button(onClick = { onPractice(task.subject, task.topic) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Quiz, null); Spacer(Modifier.width(8.dp)); Text("Take Quiz on ${task.topic}")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onOpenMaterial(task.subject) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null); Spacer(Modifier.width(8.dp)); Text("View Study Notes for ${task.subject}")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp))
            Text(if (task.completed) "Mark as incomplete" else "Mark as completed")
        }
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete task")
        }
    }
}

data class QuizQuestion(
    val questionText: String,
    val options: List<String>,
    val correctAnswer: String
) {
    val shuffledOptions: List<String> = options.shuffled(Random(questionText.hashCode().toLong()))
}

val defaultQuestionBank = mapOf(
    "Database Management Systems" to listOf(
        QuizQuestion("Which SQL command retrieves data from a table?", listOf("SELECT", "DELETE", "UPDATE", "INSERT"), "SELECT"),
        QuizQuestion("Which normal form removes partial functional dependencies?", listOf("1NF", "2NF", "3NF", "BCNF"), "2NF"),
        QuizQuestion("What does ACID stand for in database transactions?", listOf("Atomicity, Consistency, Isolation, Durability", "Accuracy, Control, Integrity, Data", "Access, Concurrency, Index, Database"), "Atomicity, Consistency, Isolation, Durability"),
        QuizQuestion("Which type of index is sorted independently of data records?", listOf("Clustered Index", "Non-clustered Index", "Hash Index", "Primary Index"), "Non-clustered Index"),
        QuizQuestion("What is a deadlock in database systems?", listOf("Two transactions waiting indefinitely for locks held by each other", "A server crash", "An index corruption", "A fast query execution"), "Two transactions waiting indefinitely for locks held by each other"),
        QuizQuestion("Which SQL clause is used to filter grouped records?", listOf("WHERE", "HAVING", "GROUP BY", "ORDER BY"), "HAVING"),
        QuizQuestion("What does DDL stand for in SQL?", listOf("Data Definition Language", "Data Direct Language", "Database Dynamic Link", "Data Design Logic"), "Data Definition Language"),
        QuizQuestion("Which join returns all records when there is a match in either left or right table?", listOf("INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL OUTER JOIN"), "FULL OUTER JOIN"),
        QuizQuestion("What is a primary key constraint guaranteed to ensure?", listOf("Uniqueness and Not Null", "Foreign key references", "Fast indexing only", "Alphabetical sorting"), "Uniqueness and Not Null"),
        QuizQuestion("Which database model represents data as tables with rows and columns?", listOf("Hierarchical Model", "Network Model", "Relational Model", "Object Model"), "Relational Model")
    ),
    "Data Structures" to listOf(
        QuizQuestion("Which data structure operates on a Last-In-First-Out (LIFO) basis?", listOf("Queue", "Stack", "Array", "Tree"), "Stack"),
        QuizQuestion("What is the average time complexity of searching in a balanced Binary Search Tree?", listOf("O(1)", "O(n)", "O(log n)", "O(n log n)"), "O(log n)"),
        QuizQuestion("Which data structure is best suited for implementing a priority queue?", listOf("Stack", "Linked List", "Binary Heap", "Hash Table"), "Binary Heap"),
        QuizQuestion("What is the worst-case time complexity of Quicksort?", listOf("O(n log n)", "O(n)", "O(n^2)", "O(log n)"), "O(n^2)"),
        QuizQuestion("Which data structure uses pointers/references to connect nodes sequentially?", listOf("Array", "Linked List", "Matrix", "Heap"), "Linked List"),
        QuizQuestion("Which data structure operates on a First-In-First-Out (FIFO) basis?", listOf("Queue", "Stack", "Array", "Tree"), "Queue"),
        QuizQuestion("What is the height of a balanced tree with n nodes?", listOf("O(n)", "O(log n)", "O(1)", "O(n log n)"), "O(log n)"),
        QuizQuestion("Which data structure allows fast insertion and deletion at both ends?", listOf("Stack", "Queue", "Deque", "Array"), "Deque"),
        QuizQuestion("What is a collision in a hash table?", listOf("Two distinct keys hashing to the same index", "Running out of memory", "Sorting failure", "Null pointer dereference"), "Two distinct keys hashing to the same index"),
        QuizQuestion("Which graph representation uses a 2D array of size V x V?", listOf("Adjacency List", "Adjacency Matrix", "Edge List", "Incidence Matrix"), "Adjacency Matrix")
    ),
    "Algorithms" to listOf(
        QuizQuestion("Which algorithm design technique is used in Merge Sort?", listOf("Greedy", "Dynamic Programming", "Divide and Conquer", "Backtracking"), "Divide and Conquer"),
        QuizQuestion("Which graph traversal algorithm uses a Queue?", listOf("Depth-First Search", "Breadth-First Search", "Dijkstra's", "Bellman-Ford"), "Breadth-First Search"),
        QuizQuestion("What problem does Dijkstra's algorithm solve?", listOf("Minimum Spanning Tree", "Shortest Path", "Maximum Flow", "Sorting"), "Shortest Path"),
        QuizQuestion("Which sorting algorithm has optimal comparison-based time complexity of O(n log n)?", listOf("Bubble Sort", "Insertion Sort", "Merge Sort", "Selection Sort"), "Merge Sort"),
        QuizQuestion("Which algorithm finds the shortest path between all pairs of vertices?", listOf("Dijkstra's", "Floyd-Warshall", "Kruskal's", "Prim's"), "Floyd-Warshall"),
        QuizQuestion("What is the time complexity of binary search on a sorted array?", listOf("O(n)", "O(log n)", "O(1)", "O(n log n)"), "O(log n)"),
        QuizQuestion("Which algorithm design strategy always chooses the most immediate benefit?", listOf("Greedy Algorithm", "Dynamic Programming", "Divide and Conquer", "Brute Force"), "Greedy Algorithm"),
        QuizQuestion("What is memoization in dynamic programming?", listOf("Caching results of expensive function calls", "Deleting memory", "Sorting arrays", "Encrypting data"), "Caching results of expensive function calls"),
        QuizQuestion("Which algorithm is used to detect cycles in a directed graph?", listOf("BFS", "DFS with recursion stack", "Binary Search", "Linear Search"), "DFS with recursion stack"),
        QuizQuestion("What is the worst-case time complexity of Bubble Sort?", listOf("O(n)", "O(log n)", "O(n^2)", "O(n log n)"), "O(n^2)")
    ),
    "Programming Fundamentals" to listOf(
        QuizQuestion("What is polymorphism in object-oriented programming?", listOf("Ability of an object to take many forms", "Hiding internal data", "Creating multiple classes", "Inheriting private fields"), "Ability of an object to take many forms"),
        QuizQuestion("Which keyword is used to inherit a class in Kotlin?", listOf("extends", ":", "implements", "inherits"), ":"),
        QuizQuestion("What is recursion?", listOf("A function calling itself", "An infinite loop", "A method with no parameters", "An exception handler"), "A function calling itself"),
        QuizQuestion("Which principle states a class should have only one reason to change?", listOf("Open/Closed", "Single Responsibility", "Liskov Substitution", "Interface Segregation"), "Single Responsibility"),
        QuizQuestion("What is encapsulation?", listOf("Bundling data and methods operating on that data within a single unit", "Inheriting from multiple parents", "Deleting unused variables", "Converting types"), "Bundling data and methods operating on that data within a single unit"),
        QuizQuestion("What is inheritance in OOP?", listOf("Mechanism where one class acquires properties of another", "Hiding data", "Creating interfaces", "Overloading methods"), "Mechanism where one class acquires properties of another"),
        QuizQuestion("What is abstraction in programming?", listOf("Hiding complex implementation details and showing only essential features", "Writing long functions", "Using global variables", "Compiling code"), "Hiding complex implementation details and showing only essential features"),
        QuizQuestion("What is a static variable?", listOf("A variable shared across all instances of a class", "A local variable", "A constant only", "A thread-local variable"), "A variable shared across all instances of a class"),
        QuizQuestion("What is an interface in object-oriented programming?", listOf("A contract defining methods a class must implement", "A physical screen", "A database table", "An executable file"), "A contract defining methods a class must implement"),
        QuizQuestion("What is a null pointer exception?", listOf("An error when attempting to access a member on a null reference", "A math overflow", "A network timeout", "A disk full error"), "An error when attempting to access a member on a null reference")
    ),
    "Operating Systems" to listOf(
        QuizQuestion("What is a process?", listOf("A program in execution", "A physical hardware chip", "A storage device partition", "A network protocol"), "A program in execution"),
        QuizQuestion("Which CPU scheduling algorithm gives minimum average waiting time?", listOf("First-Come, First-Served", "Shortest Job First", "Round Robin", "Priority Scheduling"), "Shortest Job First"),
        QuizQuestion("What is virtual memory?", listOf("An illusion of a large main memory provided by disk storage", "RAM expansion sticks", "Cache memory", "ROM storage"), "An illusion of a large main memory provided by disk storage"),
        QuizQuestion("What is a semaphore?", listOf("A synchronization tool used to solve critical section problems", "A network router", "A file system", "A compiler error"), "A synchronization tool used to solve critical section problems"),
        QuizQuestion("What is thrashing in operating systems?", listOf("A state where the system spends most time paging rather than executing", "A CPU overheating", "A disk crash", "A network storm"), "A state where the system spends most time paging rather than executing"),
        QuizQuestion("What is a thread?", listOf("A lightweight unit of execution within a process", "A heavy process", "A hardware wire", "A file stream"), "A lightweight unit of execution within a process"),
        QuizQuestion("What is a context switch?", listOf("Saving state of old process and loading state of new process", "Rebooting the OS", "Changing screen resolution", "Switching user accounts"), "Saving state of old process and loading state of new process"),
        QuizQuestion("What is a page fault?", listOf("An interrupt when a program accesses a page not currently in physical RAM", "A disk read error", "A CPU exception", "A memory leak"), "An interrupt when a program accesses a page not currently in physical RAM"),
        QuizQuestion("What is round-robin scheduling based on?", listOf("Time quantum / slice", "Process priority", "Memory size", "File count"), "Time quantum / slice"),
        QuizQuestion("What is deadlock prevention?", listOf("Ensuring at least one of the four necessary conditions for deadlock cannot hold", "Killing all processes", "Rebooting immediately", "Adding more RAM"), "Ensuring at least one of the four necessary conditions for deadlock cannot hold")
    )
)

fun getQuestionsForSubject(subject: String): List<QuizQuestion> {
    val normalized = subject.trim()
    val matchKey = defaultQuestionBank.entries.find { it.key.equals(normalized, ignoreCase = true) }
    if (matchKey != null) {
        return matchKey.value
    }

    val s = normalized.lowercase()
    return when {
        s.contains("java") || s.contains("kotlin") || s.contains("python") || s.contains("c++") || s.contains("c") || s.contains("programming") || s.contains("coding") -> listOf(
            QuizQuestion("What is Object-Oriented Programming (OOP) core principle in $normalized?", listOf("Encapsulation, Inheritance, and Polymorphism", "Global variables only", "Hardcoding execution paths", "Avoiding classes"), "Encapsulation, Inheritance, and Polymorphism"),
            QuizQuestion("How is memory management handled in modern $normalized environments?", listOf("Automatic Garbage Collection or smart pointers", "Manual disk formatting", "Ignoring memory leaks", "Hardcoding memory addresses"), "Automatic Garbage Collection or smart pointers"),
            QuizQuestion("What is the purpose of exception handling in $normalized?", listOf("Gracefully managing runtime errors and unexpected failures", "Crashing the app instantly", "Slowing down execution", "Hiding syntax errors"), "Gracefully managing runtime errors and unexpected failures"),
            QuizQuestion("What does API stand for in software development for $normalized?", listOf("Application Programming Interface", "Advanced Program Index", "Automated Process Integration", "Application Protocol Instruction"), "Application Programming Interface"),
            QuizQuestion("Which data structure provides O(1) average time complexity for key-value lookups in $normalized?", listOf("Hash Table / Map", "Linked List", "Binary Tree", "Stack"), "Hash Table / Map"),
            QuizQuestion("What is the difference between an interface and an abstract class in $normalized?", listOf("Interfaces define contracts without state; abstract classes can share base state/code", "There is no difference", "Interfaces can store instance variables", "Abstract classes cannot have methods"), "Interfaces define contracts without state; abstract classes can share base state/code"),
            QuizQuestion("What is recursion in $normalized functions?", listOf("A function calling itself to solve smaller sub-problems", "An infinite loop", "A syntax error", "A hardware interrupt"), "A function calling itself to solve smaller sub-problems"),
            QuizQuestion("Why are unit tests important when writing $normalized code?", listOf("To verify correctness of individual components and prevent regressions", "To increase code size", "To slow down deployment", "To replace user testing"), "To verify correctness of individual components and prevent regressions"),
            QuizQuestion("What is version control (e.g., Git) used for in $normalized projects?", listOf("Tracking source code changes, branching, and team collaboration", "Compiling code into binaries", "Running virtual machines", "Designing user interfaces"), "Tracking source code changes, branching, and team collaboration"),
            QuizQuestion("What is Big-O notation used to measure in $normalized algorithms?", listOf("Time and space complexity as input size grows", "Line count of source files", "Execution temperature of CPU", "Network bandwidth"), "Time and space complexity as input size grows")
        )
        else -> listOf(
            QuizQuestion("What is the foundational concept of $normalized?", listOf("Core theoretical and practical principles of $normalized", "Random guesswork", "Hardware manufacturing", "Static compilation"), "Core theoretical and practical principles of $normalized"),
            QuizQuestion("Which methodology ensures success when studying $normalized?", listOf("Active problem-solving, structured practice, and review", "Passive reading", "Skipping complex topics", "Memorizing without understanding"), "Active problem-solving, structured practice, and review"),
            QuizQuestion("What is a key metric of proficiency in $normalized?", listOf("Ability to apply concepts to solve real-world problems", "Speed of typing", "Memorizing definitions verbatim", "Avoiding practical labs"), "Ability to apply concepts to solve real-world problems"),
            QuizQuestion("How are advanced problems broken down in $normalized?", listOf("Decomposition into smaller, manageable sub-problems", "Ignoring the problem", "Guessing the answer", "Restarting the device"), "Decomposition into smaller, manageable sub-problems"),
            QuizQuestion("Which resource is most reliable when mastering $normalized?", listOf("Official documentation, textbooks, and guided practice", "Random unverified forum posts", "Outdated notes", "Guesswork"), "Official documentation, textbooks, and guided practice"),
            QuizQuestion("What role does critical analysis play in $normalized?", listOf("Evaluating evidence, methods, and logical conclusions", "Accepting all claims without proof", "Ignoring counterarguments", "Memorizing dates"), "Evaluating evidence, methods, and logical conclusions"),
            QuizQuestion("What is a common pitfall when learning $normalized?", listOf("Skipping fundamentals and rushing to advanced topics", "Practicing daily", "Asking questions", "Reviewing past errors"), "Skipping fundamentals and rushing to advanced topics"),
            QuizQuestion("How does continuous assessment help when studying $normalized?", listOf("Identifying knowledge gaps and reinforcing retention", "Creating stress only", "Wasting study time", "Providing false confidence"), "Identifying knowledge gaps and reinforcing retention"),
            QuizQuestion("What is the significance of peer discussion in $normalized?", listOf("Exposing different perspectives and collaborative problem-solving", "Copying answers blindly", "Avoiding individual study", "Creating confusion"), "Exposing different perspectives and collaborative problem-solving"),
            QuizQuestion("Why is persistence critical when mastering $normalized?", listOf("Overcoming initial hurdles leads to deep long-term mastery", "It guarantees instant perfection", "It replaces practice", "It is unnecessary"), "Overcoming initial hurdles leads to deep long-term mastery")
        )
    }
}

fun generateQuestionsForTopic(subject: String, topic: String): List<QuizQuestion> {
    val s = subject.trim()
    val t = topic.trim().ifEmpty { "Core Concepts" }

    return listOf(
        QuizQuestion(
            "What is the primary objective and core definition of $t when working with $s?",
            listOf(
                "Executing data operations, syntax, and transformations specific to $t in $s",
                "Formatting document text layout and paragraph margins for $s",
                "Configuring hardware circuit board logic gates for $t",
                "Translating classical literary prose and poetry in $s"
            ),
            "Executing data operations, syntax, and transformations specific to $t in $s"
        ),
        QuizQuestion(
            "Which primary API, module, or construct governs $t in $s?",
            listOf(
                "Built-in library methods, accessor attributes, and parameters of $t",
                "CPU registers and low-level hardware memory pointers in $s",
                "Relational database foreign key constraints for $t",
                "Grammatical sentence structure and syntax rules"
            ),
            "Built-in library methods, accessor attributes, and parameters of $t"
        ),
        QuizQuestion(
            "How is data flow and computation maintained within $t in $s?",
            listOf(
                "Through systematic method calls, parameter configuration, and execution in $t",
                "By executing asynchronous background operating system threads",
                "By writing raw recursive logic loops without $t modules",
                "By adjusting font typography styles in $s documents"
            ),
            "Through systematic method calls, parameter configuration, and execution in $t"
        ),
        QuizQuestion(
            "What is a major error, exception, or pitfall when working with $t in $s?",
            listOf(
                "Encountering syntax errors, incorrect argument types, or invalid method calls in $t",
                "Forgetting to import standard electronics hardware drivers for $s",
                "Writing excessively long narrative essays about $t",
                "Using incorrect color palettes in $s user interfaces"
            ),
            "Encountering syntax errors, incorrect argument types, or invalid method calls in $t"
        ),
        QuizQuestion(
            "Which debugging approach is most effective when implementing $t in $s?",
            listOf(
                "Inspecting object attributes, printing data types, and verifying $t outputs",
                "Random trial and error without console logging in $s",
                "Guessing expected results without running $t code",
                "Hardcoding static values permanently for $t"
            ),
            "Inspecting object attributes, printing data types, and verifying $t outputs"
        ),
        QuizQuestion(
            "What role do parameters and method arguments play in $t for $s?",
            listOf(
                "They configure input data, operational modes, and behavior for $t",
                "They restrict code execution and prevent $s problem-solving entirely",
                "They slow down CPU clock cycles arbitrarily in $t",
                "They increase physical disk storage requirements for $s"
            ),
            "They configure input data, operational modes, and behavior for $t"
        ),
        QuizQuestion(
            "In software development using $s, how is $t typically deployed?",
            listOf(
                "To process computations, analyze datasets, and automate tasks using $t",
                "To construct physical building architecture blueprints for $s",
                "To compose orchestral symphony music scores about $t",
                "To manufacture semiconductor microchips for $s"
            ),
            "To process computations, analyze datasets, and automate tasks using $t"
        ),
        QuizQuestion(
            "What is a standard benchmark or criterion for evaluating proficiency in $t and $s?",
            listOf(
                "Ability to write optimized, bug-free, and idiomatic solutions using $t in $s",
                "Typing speed measured in words per minute for $t code",
                "Memorizing textbook page numbers verbatim about $s",
                "Avoiding practical $t coding laboratories"
            ),
            "Ability to write optimized, bug-free, and idiomatic solutions using $t in $s"
        ),
        QuizQuestion(
            "Why is hands-on practice crucial when studying $t in $s?",
            listOf(
                "It builds familiarity with syntax, error handling, and workflows specific to $t",
                "It causes unnecessary keyboard wear and tear during $s coding",
                "It is a redundant exercise with no educational benefit for $t",
                "It replaces the need for logical algorithmic understanding of $s"
            ),
            "It builds familiarity with syntax, error handling, and workflows specific to $t"
        ),
        QuizQuestion(
            "What is the ultimate takeaway from successfully mastering $t in $s?",
            listOf(
                "Empowering advanced software capability and practical project building with $t in $s",
                "Inability to comprehend related $s concepts",
                "Permanent reliance on random guesswork for $t",
                "Elimination of all future $s learning needs"
            ),
            "Empowering advanced software capability and practical project building with $t in $s"
        )
    )
}

@Composable
fun PracticeScreen(
    tasks: List<StudyTask>,
    initialSubject: String = "",
    initialTopic: String = "",
    onMarkCompleted: (String) -> Unit = {}
) {
    val studentSubjects = tasks.map { it.subject }.distinct()
    val availableSubjects = studentSubjects.ifEmpty { defaultQuestionBank.keys.toList() }

    val defaultSubj = if (initialSubject.isNotBlank() && availableSubjects.contains(initialSubject)) initialSubject else availableSubjects.first()
    var selectedSubject by remember(initialSubject, availableSubjects) { mutableStateOf(defaultSubj) }
    
    val questions = remember(selectedSubject, initialTopic) {
        if (initialTopic.isNotBlank()) {
            generateQuestionsForTopic(selectedSubject, initialTopic)
        } else {
            getQuestionsForSubject(selectedSubject)
        }
    }

    var index by remember(selectedSubject) { mutableIntStateOf(0) }
    var selected by remember(selectedSubject, index) { mutableStateOf<String?>(null) }
    var score by remember(selectedSubject) { mutableIntStateOf(0) }
    var quizFinished by remember(selectedSubject) { mutableStateOf(false) }

    val passed = score >= 7
    LaunchedEffect(quizFinished, passed) {
        if (quizFinished && passed) {
            onMarkCompleted(selectedSubject)
        }
    }

    val question = questions[index.coerceAtMost(questions.lastIndex)]
    val scrollState = rememberScrollState()

    LaunchedEffect(selected) {
        if (selected != null) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(20.dp)) {
        ScreenTitle("Quick Practice", "Test your knowledge on your study subjects")
        Text("Select Subject", fontWeight = FontWeight.Bold, color = Navy, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableSubjects.forEach { subj ->
                FilterChip(
                    selected = selectedSubject == subj,
                    onClick = {
                        selectedSubject = subj
                        index = 0
                        selected = null
                        score = 0
                        quizFinished = false
                    },
                    label = { Text(subj) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))




        if (quizFinished) {
            val passed = score >= 7
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                        null,
                        tint = if (passed) Green else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (passed) "Quiz Passed! 🎉" else "Needs Improvement ⚠️",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Subject: $selectedSubject", color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.height(20.dp))
                    Text("Your Final Score", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Blue, letterSpacing = 1.5.sp)
                    Text("$score / ${questions.size}", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = if (passed) Green else MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))

                    val message = if (passed) {
                        "Congratulations! You scored 7 or higher. Completion mark unlocked! ✅"
                    } else {
                        "You scored $score out of ${questions.size}. You need at least 7 correct answers (70%) to pass. Please review your study notes and retake the quiz."
                    }
                    Text(message, color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(24.dp))
            if (passed) {
                Button(
                    onClick = {
                        index = 0
                        selected = null
                        score = 0
                        quizFinished = false
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Quiz Completed ✅", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        index = 0
                        selected = null
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
