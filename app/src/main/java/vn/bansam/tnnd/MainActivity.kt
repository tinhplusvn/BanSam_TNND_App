package vn.bansam.tnnd

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import vn.bansam.tnnd.data.*
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val vm by viewModels<MainVM> {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainVM(applicationContext) as T
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App(vm, this) }
    }
}

class MainVM(private val context: Context) : ViewModel() {
    private val dao=AppDb.get(context).dao()
    val people=dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val queryFlow = MutableStateFlow("")
    private val groupFlow = MutableStateFlow("Tất cả")
    var query: String
        get() = queryFlow.value
        set(v) { queryFlow.value = v }
    var group: String
        get() = groupFlow.value
        set(v) { groupFlow.value = v }
    var dark by mutableStateOf(context.getSharedPreferences("settings",0).getBoolean("dark",false))
    init { viewModelScope.launch {
        if(dao.count()==0) {
            val seed=context.assets.open("seed.json").bufferedReader().readText()
            val arr=org.json.JSONArray(seed)
            val list=(0 until arr.length()).map{ i ->
                val o=arr.getJSONObject(i)
                Person(name=o.optString("name"),dob=o.optString("dob"),gender=o.optString("gender"),
                    address=o.optString("address"),className=o.optString("className"),father=o.optString("father"),
                    mother=o.optString("mother"),phone=o.optString("phone"))
            }
            dao.insertAll(list)
        }
    }}
    val filtered: StateFlow<List<Person>> = combine(people, queryFlow, groupFlow) { list,q,g ->
        list.filter { p ->
            val hay="${p.name} ${p.father} ${p.mother} ${p.phone} ${p.address} ${p.className}".lowercase()
            val matches=hay.contains(q.trim().lowercase())
            val gg=groupForAge(ageOnDate(p.dob))
            matches && (g=="Tất cả" || gg==g)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(p:Person)=viewModelScope.launch { if(p.id==0L) dao.insert(p) else dao.update(p) }
    fun delete(p:Person)=viewModelScope.launch { dao.delete(p) }
    fun import(list:List<Person>)=viewModelScope.launch { dao.deleteAll(); dao.insertAll(list) }
    fun toggleDark(){dark=!dark;context.getSharedPreferences("settings",0).edit().putBoolean("dark",dark).apply()}
}

@Composable fun App(vm:MainVM, context:Context) {
    val list by vm.filtered.collectAsState()
    var editing by remember { mutableStateOf<Person?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    val snackbar=remember{SnackbarHostState()}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")){uri->
        if(uri!=null) try { ExcelUtil.export(context,uri,vm.people.value) } catch(e:Exception){ }
    }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null) try { vm.import(ExcelUtil.import(context,uri)) } catch(e:Exception) { }
    }
    MaterialTheme(colorScheme=if(vm.dark) darkColorScheme() else lightColorScheme()) {
        Scaffold(
            topBar={ TopAppBar(
                title={Text("TN-NĐ Bản Sấm")},
                actions={
                    IconButton({showFilter=true}){Icon(Icons.Default.FilterList,"Lọc")}
                    IconButton({vm.toggleDark()}){Icon(if(vm.dark) Icons.Default.LightMode else Icons.Default.DarkMode,"Giao diện")}
                    IconButton({export.launch("DanhSach_TNND_BanSam.xlsx")}){Icon(Icons.Default.FileDownload,"Xuất Excel")}
                    IconButton({import.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-excel"))}){Icon(Icons.Default.FileUpload,"Nhập Excel")}
                }
            )},
            floatingActionButton={FloatingActionButton({editing=null;showForm=true}){Icon(Icons.Default.Add,"Thêm")}},
            snackbarHost={SnackbarHost(snackbar)}
        ){ pad->
            Column(Modifier.padding(pad).fillMaxSize().padding(horizontal=12.dp)) {
                OutlinedTextField(vm.query,{vm.query=it},Modifier.fillMaxWidth().padding(top=8.dp),
                    singleLine=true,label={Text("Tìm kiếm")},leadingIcon={Icon(Icons.Default.Search,null)})
                Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween){
                    Stat("Tổng",vm.people.value.size)
                    Stat("Mầm non",vm.people.value.count{groupForAge(ageOnDate(it.dob))=="Mầm non"})
                    Stat("Tiểu học",vm.people.value.count{groupForAge(ageOnDate(it.dob))=="Tiểu học"})
                    Stat("THCS",vm.people.value.count{groupForAge(ageOnDate(it.dob))=="THCS"})
                }
                Text("Đang hiển thị ${list.size} người",style=MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=90.dp)){
                    items(list,key={it.id}){p->
                        PersonCard(p,{editing=p;showForm=true},{vm.delete(p)})
                    }
                }
            }
        }
        if(showForm) PersonDialog(editing,{showForm=false},{vm.save(it);showForm=false})
        if(showFilter) FilterDialog(vm.group,{vm.group=it;showFilter=false})
    }
}
@Composable fun Stat(label:String,n:Int){ Column(horizontalAlignment=Alignment.CenterHorizontally){Text("$n",style=MaterialTheme.typography.titleMedium);Text(label,style=MaterialTheme.typography.labelSmall)} }
@Composable fun PersonCard(p:Person,onEdit:()->Unit,onDelete:()->Unit){
    var confirm by remember{mutableStateOf(false)}
    Card(shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(12.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text(p.name,style=MaterialTheme.typography.titleMedium);Text("${ageOnDate(p.dob)?.let{"$it tuổi"}?:"Chưa rõ tuổi"} • ${groupForAge(ageOnDate(p.dob))} • ${p.className.ifBlank{"Chưa có lớp"}}",style=MaterialTheme.typography.bodySmall)}
                Row{IconButton(onEdit){Icon(Icons.Default.Edit,null)};IconButton({confirm=true}){Icon(Icons.Default.Delete,null)}}
            }
            Text("${p.gender} • ${p.address.ifBlank{"Chưa có địa chỉ"}}",style=MaterialTheme.typography.bodySmall)
            Text("Bố: ${p.father.ifBlank{"—"}} | Mẹ: ${p.mother.ifBlank{"—"}}",style=MaterialTheme.typography.bodySmall)
            if(p.phone.isNotBlank()) Text("SĐT PH: ${p.phone}",style=MaterialTheme.typography.bodySmall)
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Xóa dữ liệu?")},text={Text("Xóa ${p.name} khỏi danh sách?")},confirmButton={TextButton({confirm=false;onDelete()}){Text("Xóa")}},dismissButton={TextButton({confirm=false}){Text("Hủy")}})
}
@Composable fun PersonDialog(existing:Person?,close:()->Unit,save:(Person)->Unit){
    var p by remember(existing){mutableStateOf(existing?:Person(name=""))}
    AlertDialog(onDismissRequest=close,title={Text(if(existing==null)"Thêm thiếu niên/nhi đồng" else "Sửa thông tin")},
        text={Column(Modifier.fillMaxWidth().heightIn(max=520.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
            Field("Họ và tên",p.name){p=p.copy(name=it)}
            Field("Ngày sinh (yyyy-MM-dd)",p.dob){p=p.copy(dob=it)}
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){Field("Giới tính",p.gender,{p=p.copy(gender=it)},Modifier.weight(1f));Field("Lớp",p.className,{p=p.copy(className=it)},Modifier.weight(1f))}
            Text("Tuổi: ${ageOnDate(p.dob)?.toString()?:"—"}   Khối: ${groupForAge(ageOnDate(p.dob))}",style=MaterialTheme.typography.labelMedium)
            AddressField(p.address){p=p.copy(address=it)}
            Field("Tên bố",p.father){p=p.copy(father=it)}
            Field("Tên mẹ",p.mother){p=p.copy(mother=it)}
            Field("SĐT phụ huynh",p.phone){p=p.copy(phone=it)}
        }},
        confirmButton={Button({if(p.name.isNotBlank())save(p)}){Text("Lưu")}},
        dismissButton={TextButton(close){Text("Hủy")}})
}
@Composable fun Field(label:String,value:String,onChange:(String)->Unit,modifier:Modifier=Modifier.fillMaxWidth()) {
    OutlinedTextField(value,onChange,modifier,singleLine=true,label={Text(label)})
}
@Composable fun AddressField(value:String,onChange:(String)->Unit){
    var open by remember{mutableStateOf(false)}
    Box{
        OutlinedTextField(value,onChange,Modifier.fillMaxWidth(),singleLine=true,label={Text("Địa chỉ (thôn)")},trailingIcon={IconButton({open=true}){Icon(Icons.Default.ArrowDropDown,null)}})
        DropdownMenu(open,{open=false}){ listOf("Thôn Sấm 1","Thôn Sấm 2","Thôn Sấm 3","Thôn Bãi Cả").forEach{DropdownMenuItem({onChange(it);open=false},{Text(it)})}}
    }
}
@Composable fun FilterDialog(selected:String,close:(String)->Unit){
    AlertDialog(onDismissRequest={close(selected)},title={Text("Lọc theo khối")},text={Column{listOf("Tất cả","Mầm non","Tiểu học","THCS","Chưa xác định","Dưới 3 tuổi","Trên 15 tuổi").forEach{g->TextButton({close(g)},Modifier.fillMaxWidth()){Text(if(g==selected)"✓ $g" else g)}}}},confirmButton={})
}

