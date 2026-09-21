package vn.bansam.tnnd.data

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ExcelUtil {
    private val headers = listOf("STT","Họ và tên","Ngày sinh","Giới tính","Địa chỉ","Lớp","Họ tên Bố","Họ tên Mẹ","SĐT PH")

    fun export(context: Context, uri: Uri, people: List<Person>) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(out).use { zip ->
                add(zip, "[Content_Types].xml", contentTypes())
                add(zip, "_rels/.rels", rels())
                add(zip, "xl/workbook.xml", workbook())
                add(zip, "xl/_rels/workbook.xml.rels", workbookRels())
                add(zip, "xl/worksheets/sheet1.xml", sheet(people))
                add(zip, "xl/styles.xml", styles())
            }
        }
    }

    fun import(context: Context, uri: Uri): List<Person> {
        val files = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                while (true) {
                    val e = zis.nextEntry ?: break
                    if (!e.isDirectory) files[e.name] = zis.readBytes()
                }
            }
        } ?: return emptyList()
        val sheet = files["xl/worksheets/sheet1.xml"] ?: return emptyList()
        val shared = files["xl/sharedStrings.xml"]?.let { parseShared(it) } ?: emptyList()
        val rows = parseSheet(sheet, shared)
        if (rows.isEmpty()) return emptyList()
        val start = if (rows.firstOrNull()?.firstOrNull()?.equals("STT", true) == true) 1 else 0
        return rows.drop(start).mapNotNull { r ->
            if (r.size < 2 || r.getOrNull(1).orEmpty().isBlank()) return@mapNotNull null
            Person(
                name=r.getOrNull(1).orEmpty(), dob=normalizeDate(r.getOrNull(2).orEmpty()),
                gender=r.getOrNull(3).orEmpty(), address=r.getOrNull(4).orEmpty(),
                className=r.getOrNull(5).orEmpty(), father=r.getOrNull(6).orEmpty(),
                mother=r.getOrNull(7).orEmpty(), phone=r.getOrNull(8).orEmpty()
            )
        }
    }

    private fun parseShared(bytes: ByteArray): List<String> {
        val p = Xml.newPullParser(); p.setInput(bytes.inputStream(), "UTF-8")
        val out=mutableListOf<String>(); var text=""
        var event=p.eventType
        while(event != XmlPullParser.END_DOCUMENT){
            if(event==XmlPullParser.START_TAG && p.name=="t") text=p.nextText()
            else if(event==XmlPullParser.END_TAG && p.name=="si") out += text
            event=p.next()
        }
        return out
    }
    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val p=Xml.newPullParser(); p.setInput(bytes.inputStream(),"UTF-8")
        val result=mutableListOf<List<String>>(); var row=mutableListOf<String>(); var cellType=""; var cellRef=""; var value=""
        var event=p.eventType
        while(event != XmlPullParser.END_DOCUMENT){
            if(event==XmlPullParser.START_TAG){
                when(p.name){
                    "row" -> row=mutableListOf()
                    "c" -> { cellType=p.getAttributeValue(null,"t") ?: ""; cellRef=p.getAttributeValue(null,"r") ?: "" }
                    "v" -> value=p.nextText()
                    "t" -> value=p.nextText()
                }
            } else if(event==XmlPullParser.END_TAG){
                when(p.name){
                    "c" -> {
                        val v=if(cellType=="s") shared.getOrNull(value.toIntOrNull() ?: -1) ?: "" else value
                        val col=cellRef.takeWhile{it.isLetter()}.fold(0){acc,ch->acc*26+(ch.uppercaseChar()-'A'+1)}-1
                        while(row.size<=col) row.add("")
                        row[col]=v
                    }
                    "row" -> result+=row
                }
            }
            event=p.next()
        }
        return result
    }
    private fun normalizeDate(s:String):String {
        if(s.matches(Regex("\\d{4}-\\d{1,2}-\\d{1,2}"))) return s
        val parts=s.split("/", "-", ".")
        if(parts.size==3) {
            val a=parts.map{it.trim().toIntOrNull()}
            if(a.all{it!=null}) {
                val (d,m,y)=a
                if(y!!>1900) return "%04d-%02d-%02d".format(y,m!!,d!!)
            }
        }
        return s
    }
    private fun esc(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    private fun cell(ref:String,v:String)= """<c r="$ref" t="inlineStr"><is><t>${esc(v)}</t></is></c>"""
    private fun sheet(people:List<Person>):String {
        val all=mutableListOf(headers)
        people.forEachIndexed{ i,p -> all += listOf("${i+1}",p.name,p.dob,p.gender,p.address,p.className,p.father,p.mother,p.phone) }
        val rows=all.mapIndexed{ri,vals ->
            val cells=vals.mapIndexed{ci,v -> cell(colName(ci)+ (ri+1),v)}.joinToString("")
            "<row r=\"${ri+1}\">$cells</row>"
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rows</sheetData></worksheet>"""
    }
    private fun colName(n:Int):String { var x=n+1; var s=""; while(x>0){val r=(x-1)%26;s=('A'.code+r).toChar()+s;x=(x-1)/26};return s }
    private fun add(z:ZipOutputStream,name:String,data:String){z.putNextEntry(ZipEntry(name));z.write(data.toByteArray(StandardCharsets.UTF_8));z.closeEntry()}
    private fun contentTypes()="""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
    private fun rels()="""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private fun workbook()="""<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="DanhSach" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    private fun workbookRels()="""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
    private fun styles()="""<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border/></borders><cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellXfs></styleSheet>"""
}
