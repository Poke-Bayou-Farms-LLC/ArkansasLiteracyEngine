package com.designategold7.arkansasliteracyengine

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// --- 1. ARKANSAS SYSTEM BRANDING ---
val ArkansasCardinal = Color(0xFF990000)
val ArkansasSlate = Color(0xFF475569)
val MathBlue = Color(0xFF2563EB)
val RlaGreen = Color(0xFF16A34A)
val SciencePurple = Color(0xFF7C3AED)
val SocialOrange = Color(0xFFEA580C)
val WageGold = Color(0xFFD97706)
val SurfaceGray = Color(0xFFF8FAFC)

@Composable
fun ArkansasLiteracyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = ArkansasCardinal, secondary = ArkansasSlate, background = SurfaceGray),
        shapes = Shapes(medium = RoundedCornerShape(12.dp)),
        content = content
    )
}

// --- 2. DATA ENTITIES & ROOM DB ---
enum class UserRole { STUDENT, TEACHER, ADMIN }

@Entity(tableName = "users")
data class UserEntity(@PrimaryKey val username: String, val pinOrPass: String, val role: UserRole, val institutionId: String)

@Entity(tableName = "questions")
data class QuestionEntity(@PrimaryKey(autoGenerate = true) val id: Int = 0, val subject: String, val passage: String, val question: String, val optionA: String, val optionB: String, val optionC: String, val optionD: String, val correctAnswer: String, val explanation: String)

@Entity(tableName = "study_sessions")
data class StudySessionEntity(@PrimaryKey(autoGenerate = true) val id: Int = 0, val studentUsername: String, val subject: String, val timestamp: Long, val institutionId: String, val deviceId: String, val isSynced: Boolean = false)

@Dao
interface AppDao {
    @Query("SELECT * FROM users WHERE username = :u COLLATE NOCASE AND pinOrPass = :p LIMIT 1")
    fun authenticate(u: String, p: String): UserEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUser(u: UserEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertQuestion(q: QuestionEntity)
    @Query("SELECT * FROM questions")
    fun getAllQuestions(): List<QuestionEntity>
    @Insert
    fun insertSession(s: StudySessionEntity)
    @Query("SELECT * FROM study_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): List<StudySessionEntity>
    @Query("DELETE FROM questions")
    fun wipeQuestions()
    @Query("SELECT * FROM study_sessions WHERE isSynced = 0")
    fun getUnsyncedSessions(): List<StudySessionEntity>
    @Query("UPDATE study_sessions SET isSynced = 1 WHERE id IN (:ids)")
    fun markSessionsSynced(ids: List<Int>)
}

@Database(entities = [UserEntity::class, QuestionEntity::class, StudySessionEntity::class], version = 13, exportSchema = false)
abstract class LiteracyDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    companion object {
        fun getDatabase(ctx: Context): LiteracyDatabase = Room.databaseBuilder(ctx, LiteracyDatabase::class.java, "literacy_db").fallbackToDestructiveMigration().build()
    }
}

// --- 3. REPOSITORY ---
class AppRepository(private val dao: AppDao) {
    suspend fun auth(u: String, p: String) = withContext(Dispatchers.IO) { dao.authenticate(u, p) }
    suspend fun getQuestions() = withContext(Dispatchers.IO) { dao.getAllQuestions() }
    suspend fun getSessions() = withContext(Dispatchers.IO) { dao.getAllSessions() }
    suspend fun logSession(user: UserEntity, sub: String) = withContext(Dispatchers.IO) {
        dao.insertSession(StudySessionEntity(studentUsername = user.username, subject = sub, timestamp = System.currentTimeMillis(), institutionId = user.institutionId, deviceId = "AR-EDGE-NODE"))
    }
    suspend fun syncLiveQuestions(newQs: List<QuestionEntity>) = withContext(Dispatchers.IO) {
        dao.wipeQuestions()
        newQs.forEach { dao.insertQuestion(it) }
    }
    suspend fun getUnsyncedSessions() = withContext(Dispatchers.IO) { dao.getUnsyncedSessions() }
    suspend fun markSessionsSynced(ids: List<Int>) = withContext(Dispatchers.IO) { dao.markSessionsSynced(ids) }

    suspend fun seed() = withContext(Dispatchers.IO) {
        if (dao.getAllQuestions().isEmpty()) {
            dao.insertUser(UserEntity("PokeBayouFarms", "demo", UserRole.STUDENT, "ASU-Jonesboro"))
            dao.insertUser(UserEntity("teacher@pokebayou.com", "demo", UserRole.TEACHER, "ASU-Jonesboro"))
            dao.insertUser(UserEntity("admin@pokebayou.com", "demo", UserRole.ADMIN, "Poke Bayou LLC"))

            val seedPack = listOf(
                QuestionEntity(subject = "Math", passage = "A farmer in Jonesboro has 50 acres of land. Historically, each acre yields 40 bales of hay per season.", question = "If each acre yields 40 bales, how many total bales will the farm produce?", optionA = "200", optionB = "2000", optionC = "500", optionD = "100", correctAnswer = "B", explanation = "To find the total, consider the relationship between the single unit (one acre) and the total area (50 acres). What mathematical operation allows us to scale a value repeatedly?"),
                QuestionEntity(subject = "Social Studies", passage = "Act 1227 mandates digital accessibility across all Arkansas state agencies, ensuring that technology is usable by individuals with disabilities.", question = "What is the primary goal of Act 1227?", optionA = "Tax reform", optionB = "Digital accessibility", optionC = "Agricultural subsidies", optionD = "Highway funding", correctAnswer = "B", explanation = "Review the text's specific phrasing about who the Act is designed to help. Does it mention technology access?"),
                QuestionEntity(subject = "Science", passage = "Photosynthesis is the process by which plants use sunlight, water, and carbon dioxide to create oxygen and energy in the form of sugar.", question = "Which of the following is released as a byproduct of photosynthesis?", optionA = "Oxygen", optionB = "Nitrogen", optionC = "Hydrogen", optionD = "Carbon Dioxide", correctAnswer = "A", explanation = "Look closely at the word 'byproduct'. The text lists what goes in and what comes out. Which of the options is listed as an output?")
            )
            seedPack.forEach { dao.insertQuestion(it) }
        }
    }
}

// --- 4. REFINED SOCRATIC TUTOR ENGINE ---
interface ITutorEngine {
    fun streamSocraticHint(question: QuestionEntity, selectedOption: String): Flow<String>
}

class MockTutorEngine : ITutorEngine {
    override fun streamSocraticHint(question: QuestionEntity, selectedOption: String): Flow<String> = flow {
        val initSequence = listOf(
            "> Arkansas Fog Node: Request Received\n",
            "> Syncing Local Vector Index...\n",
            "> Analyzing Student Friction Point...\n\n"
        )
        for (line in initSequence) { emit(line); delay(200) }

        val openers = listOf(
            "I see where you're coming from with Option $selectedOption. It's a common trap.",
            "That's an interesting perspective. Let's look at the logic again.",
            "Option $selectedOption is a strong distractor, but there's a specific detail we missed."
        )

        val connectors = listOf(
            "If we look back at the passage, notice how it mentions",
            "Think about the relationship between the keywords and",
            "In a real-world scenario, we'd have to consider"
        )

        val voiceOpener = openers.random()
        val voiceConnector = connectors.random()
        
        val fullResponse = """
            $voiceOpener
            
            $voiceConnector "${question.passage.take(35)}..."
            
            ${question.explanation}
            
            Does that change how you'd look at the relationship between the variables?
        """.trimIndent()

        val tokens = fullResponse.split(" ")
        for (token in tokens) {
            emit("$token ")
            delay((60..120).random().toLong()) 
        }
    }
}

// --- 5. VIEWMODEL ---
class AppViewModel(
    private val repo: AppRepository,
    private val tutorEngine: ITutorEngine
) : ViewModel() {

    var currentScreen by mutableStateOf(AppScreen.LAUNCHPAD)
    var currentUser by mutableStateOf<UserEntity?>(null)
    var questionList by mutableStateOf<List<QuestionEntity>>(emptyList())
    var currentQuestionIndex by mutableIntStateOf(0)
    var isNodeOnline by mutableStateOf(false)
    var dynamicIp by mutableStateOf<String?>(null)
    var sessions by mutableStateOf<List<StudySessionEntity>>(emptyList())

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val httpClient = OkHttpClient()

    val currentQuestion: QuestionEntity? get() = questionList.getOrNull(currentQuestionIndex)
    val earnedBadges: Int get() = sessions.filter { it.studentUsername == currentUser?.username }.map { it.subject }.distinct().size
    val totalSessionsLogged: Int get() = sessions.filter { it.studentUsername == currentUser?.username }.size

    fun getTutorStream(question: QuestionEntity, selectedOption: String): Flow<String> {
        return tutorEngine.streamSocraticHint(question, selectedOption)
    }

    fun quickLogin(role: UserRole) {
        viewModelScope.launch {
            currentUser = when (role) {
                UserRole.STUDENT -> UserEntity("CohortA_Student", "demo", UserRole.STUDENT, "ASU-Jonesboro")
                UserRole.TEACHER -> UserEntity("Barbara_Warren", "demo", UserRole.TEACHER, "ASU-Jonesboro")
                UserRole.ADMIN -> UserEntity("Michael_Lindsey", "demo", UserRole.ADMIN, "Poke Bayou LLC")
            }
            loadSessions()
            loadAllQuestions()
            currentScreen = when (role) {
                UserRole.STUDENT -> AppScreen.STUDENT_DASHBOARD
                UserRole.TEACHER -> AppScreen.TEACHER_DASHBOARD
                UserRole.ADMIN -> AppScreen.ADMIN_DASHBOARD
            }
        }
    }

    fun logout() {
        currentUser = null
        currentScreen = AppScreen.LAUNCHPAD
    }

    fun initNsd(ctx: Context) {
        val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(s: String) {}
            override fun onServiceFound(s: NsdServiceInfo) {
                if (s.serviceName.contains("ArkansasFogNode")) nsd.resolveService(s, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, e: Int) {}
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        dynamicIp = si.host.hostAddress
                        isNodeOnline = true
                    }
                })
            }
            override fun onServiceLost(s: NsdServiceInfo) { isNodeOnline = false }
            override fun onDiscoveryStopped(s: String) {}
            override fun onStartDiscoveryFailed(s: String, e: Int) {}
            override fun onStopDiscoveryFailed(s: String, e: Int) {}
        }
        nsd.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun loadAllQuestions() {
        viewModelScope.launch(Dispatchers.IO) {
            val q = repo.getQuestions()
            withContext(Dispatchers.Main) { questionList = q }
        }
    }

    fun loadSessions() { viewModelScope.launch { sessions = repo.getSessions() } }

    fun logSession(sub: String) {
        viewModelScope.launch {
            currentUser?.let {
                repo.logSession(it, sub)
                loadSessions()
            }
        }
    }

    fun startSession(subject: String, goalType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val allQ = repo.getQuestions()
            val filteredList = allQ.filter { it.subject.contains(subject, ignoreCase = true) }
            withContext(Dispatchers.Main) {
                questionList = if (filteredList.isNotEmpty()) filteredList else allQ
                currentQuestionIndex = 0
                currentScreen = AppScreen.STUDENT_TUTOR
            }
        }
    }

    fun next() { if (currentQuestionIndex < questionList.size - 1) currentQuestionIndex++ else currentQuestionIndex = 0 }

    fun runLacesExport(ctx: Context, onComplete: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val allSessions = repo.getSessions()
            val file = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "LACES_Report.csv")
            try {
                file.writer().use { out ->
                    out.write("Student_ID,Subject_Area,Date,Institution\n")
                    allSessions.forEach { out.write("${it.studentUsername},${it.subject},${it.timestamp},${it.institutionId}\n") }
                }
                withContext(Dispatchers.Main) { onComplete(file.absolutePath) }
            } catch (e: Exception) { withContext(Dispatchers.Main) { onComplete(null) } }
        }
    }
}

// --- 6. ROUTER & KIOSK LAUNCHPAD ---
enum class AppScreen { LAUNCHPAD, STUDENT_DASHBOARD, STUDENT_TUTOR, TEACHER_DASHBOARD, ADMIN_DASHBOARD, WAGE_TRACKER }

@Composable
fun AppRouter(vm: AppViewModel) {
    Crossfade(vm.currentScreen, label = "ScreenTransition") { s ->
        when (s) {
            AppScreen.LAUNCHPAD -> Launchpad(vm)
            AppScreen.STUDENT_DASHBOARD -> StudentPathfinderDashboard(vm)
            AppScreen.STUDENT_TUTOR -> TutorScreen(vm)
            AppScreen.TEACHER_DASHBOARD -> InstructorInsightsDashboard(vm)
            AppScreen.ADMIN_DASHBOARD -> FoundryAdminDashboard(vm)
            AppScreen.WAGE_TRACKER -> WageTrackerDashboard(vm)
        }
    }
}

@Composable
fun Launchpad(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Logo", tint = ArkansasCardinal, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(16.dp))
        Text("Arkansas Literacy Engine", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = ArkansasCardinal, textAlign = TextAlign.Center)
        Text("Zero-Trust Offline Education Node", fontSize = 16.sp, color = ArkansasSlate, modifier = Modifier.padding(bottom = 48.dp))

        Button(
            onClick = { vm.quickLogin(UserRole.STUDENT) },
            modifier = Modifier.fillMaxWidth(0.9f).height(72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MathBlue),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.School, contentDescription = "Student", modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Text("Enter Student Dashboard", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { vm.quickLogin(UserRole.TEACHER) },
            modifier = Modifier.fillMaxWidth(0.9f).height(72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RlaGreen),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Groups, contentDescription = "Teacher", modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Text("Instructor Insights Portal", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = { vm.quickLogin(UserRole.ADMIN) },
            modifier = Modifier.fillMaxWidth(0.9f).height(72.dp),
            border = BorderStroke(2.dp, ArkansasSlate),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ArkansasSlate)
        ) {
            Icon(Icons.Default.SettingsSuggest, contentDescription = "Admin", modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Text("Poke Bayou Hub (Admin)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.weight(1f))
        ComplianceFooter()
    }
}

// --- 7. DASHBOARDS (Stubs for Demo) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentPathfinderDashboard(vm: AppViewModel) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Mission Control", color = ArkansasCardinal) }, actions = { TextButton(onClick = { vm.logout() }) { Text("Logout") } }) },
        bottomBar = { ComplianceFooter() }
    ) { p ->
        Column(Modifier.padding(p).padding(16.dp).verticalScroll(rememberScrollState())) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ArkansasCardinal)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Welcome back, Student.", fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Button(onClick = { vm.startSession("Math", "Quick") }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ArkansasCardinal), modifier = Modifier.padding(top = 12.dp)) { Text("Resume Math") }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("GED Modules", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SubjectCard("Math", Icons.Default.Calculate, MathBlue, 0.4f, Modifier.weight(1f)) { vm.startSession("Math", "Quick") }
                SubjectCard("Language Arts", Icons.AutoMirrored.Filled.MenuBook, RlaGreen, 0.7f, Modifier.weight(1f)) { vm.startSession("Language Arts", "Quick") }
            }
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth().clickable { vm.currentScreen = AppScreen.WAGE_TRACKER }, colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, contentDescription = "WAGE", tint = WageGold)
                    Spacer(Modifier.width(16.dp))
                    Text("Arkansas WAGE™ Roadmap", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorScreen(vm: AppViewModel) {
    val q = vm.currentQuestion ?: return
    var sel by remember(q.id) { mutableStateOf<String?>(null) }
    var sub by remember(q.id) { mutableStateOf(false) }
    var tut by remember(q.id) { mutableStateOf(false) }
    var txt by remember(q.id) { mutableStateOf("") }

    LaunchedEffect(tut) { if (tut) vm.getTutorStream(q, sel ?: "").collectLatest { txt += it } }

    Scaffold(topBar = { TopAppBar(title = { Text(q.subject) }, navigationIcon = { IconButton(onClick = { vm.currentScreen = AppScreen.STUDENT_DASHBOARD }) { Icon(Icons.Default.ArrowBack, "") } }) }) { p ->
        LazyColumn(Modifier.padding(p).padding(16.dp).fillMaxSize()) {
            item { Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) { Text(q.passage, Modifier.padding(16.dp)) } }
            item { Text(q.question, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            val options = listOf("A" to q.optionA, "B" to q.optionB, "C" to q.optionC, "D" to q.optionD)
            items(options) { (k, v) ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(!sub) { sel = k }, colors = CardDefaults.cardColors(containerColor = if(sel == k) Color(0xFFE0F2FE) else Color.White), border = BorderStroke(1.dp, Color.LightGray)) {
                    Text("$k. $v", Modifier.padding(16.dp))
                }
            }
            item {
                if (!sub && sel != null) Button({ sub = true; vm.logSession(q.subject) }, Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Check Answer") }
                if (sub) {
                    if (sel == q.correctAnswer) Button({ vm.next() }, Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = RlaGreen)) { Text("Correct! Next Question") }
                    else if (!tut) Button({ tut = true }, Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = ArkansasCardinal)) { Text("Run Edge Diagnostic") }
                }
                if (tut) {
                    Box(Modifier.padding(top = 16.dp).background(Color(0xFF0F172A), RoundedCornerShape(8.dp)).padding(16.dp)) {
                        Text(txt, color = Color(0xFF10B981), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WageTrackerDashboard(vm: AppViewModel) {
    Scaffold(topBar = { TopAppBar(title = { Text("WAGE™ Certification") }, navigationIcon = { IconButton(onClick = { vm.currentScreen = AppScreen.STUDENT_DASHBOARD }) { Icon(Icons.Default.ArrowBack, "") } }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            Text("Career Certifications", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))
            WageCard("Industrial", 0.35f, WageGold)
            Spacer(Modifier.height(12.dp))
            WageCard("Clerical", 0.10f, MathBlue)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorInsightsDashboard(vm: AppViewModel) {
    Scaffold(topBar = { TopAppBar(title = { Text("Instructor Insights") }, actions = { TextButton(onClick = { vm.logout() }) { Text("Logout") } }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            Text("Cohort A Performance", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Row(Modifier.padding(16.dp)) { Text("Sarah Jenkins"); Spacer(Modifier.weight(1f)); Text("Math: Progressing", color = RlaGreen) } }
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Row(Modifier.padding(16.dp)) { Text("David Cole"); Spacer(Modifier.weight(1f)); Text("RLA: Needs Review", color = ArkansasCardinal) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoundryAdminDashboard(vm: AppViewModel) {
    var load by remember { mutableFloatStateOf(12.5f) }
    Scaffold(topBar = { TopAppBar(title = { Text("Poke Bayou Hub Admin") }, actions = { TextButton(onClick = { vm.logout() }) { Text("Logout") } }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            Text("Edge Telemetry", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("NPU Load: ${load}%", fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { load/100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Button(onClick = { load = 100f }, colors = ButtonDefaults.buttonColors(containerColor = ArkansasCardinal)) { Text("Simulate Max Load") }
                }
            }
        }
    }
}

// --- HELPER COMPONENTS ---

@Composable
fun SubjectCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, progress: Float, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.height(120.dp).clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, "", tint = color)
            Text(title, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun WageCard(title: String, progress: Float, color: Color) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = color)
        }
    }
}

@Composable
fun ComplianceFooter() {
    Surface(color = Color.White, shadowElevation = 4.dp) {
        Text("Data Residency: Local-Only (NIST 800-53 Compliant) • ASU-Jonesboro Pilot", modifier = Modifier.fillMaxWidth().padding(12.dp), textAlign = TextAlign.Center, fontSize = 10.sp, color = ArkansasSlate)
    }
}

// --- ACTIVITY ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArkansasLiteracyTheme {
                val db = LiteracyDatabase.getDatabase(LocalContext.current)
                val repo = AppRepository(db.appDao())
                val engine: ITutorEngine = MockTutorEngine()
                val vm: AppViewModel = viewModel(factory = object: ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repo, engine) as T
                })

                LaunchedEffect(Unit) { repo.seed(); vm.initNsd(applicationContext) }
                AppRouter(vm)
            }
        }
    }
}
