package com.example.communication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object PdfReportGenerator {
    fun generateModuleReport(
        context: Context,
        moduleName: String,
        vehicleName: String,
        dtcs: List<String>,
        liveData: String,
        repairProcedures: String
    ): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = document.startPage(pageInfo)
        
        val canvas: Canvas = page.canvas
        val paint = Paint()
        paint.color = Color.BLACK
        
        // Header
        paint.textSize = 24f
        paint.isFakeBoldText = true
        var y = 60f
        canvas.drawText("ProDiag Elite - Diagnostic Report", 50f, y, paint)
        
        // Divider
        y += 20f
        paint.strokeWidth = 2f
        canvas.drawLine(50f, y, 545f, y, paint)
        y += 40f
        
        // Vehicle Info
        paint.textSize = 16f
        paint.isFakeBoldText = false
        canvas.drawText("Vehicle: $vehicleName", 50f, y, paint)
        y += 30f
        canvas.drawText("Module: $moduleName", 50f, y, paint)
        y += 40f
        
        // DTCs
        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText("Detected DTCs:", 50f, y, paint)
        y += 25f
        
        paint.textSize = 14f
        paint.isFakeBoldText = false
        if (dtcs.isEmpty()) {
            canvas.drawText("No DTCs detected.", 50f, y, paint)
            y += 25f
        } else {
            for (dtc in dtcs) {
                canvas.drawText("- $dtc", 50f, y, paint)
                y += 25f
            }
        }
        y += 15f
        
        // Live Data
        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText("Live Data Snapshot:", 50f, y, paint)
        y += 25f
        
        paint.textSize = 14f
        paint.isFakeBoldText = false
        val liveDataLines = liveData.split("\n")
        for (line in liveDataLines) {
            canvas.drawText(line, 50f, y, paint)
            y += 25f
        }
        y += 15f
        
        // Repair Procedures
        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText("Recommended Repair Procedures:", 50f, y, paint)
        y += 25f
        
        paint.textSize = 14f
        paint.isFakeBoldText = false
        val repairLines = repairProcedures.split("\n")
        for (line in repairLines) {
            canvas.drawText(line, 50f, y, paint)
            y += 25f
        }
        
        document.finishPage(page)
        
        val dir = context.cacheDir
        val file = File(dir, "Report_${moduleName.replace(" ", "_")}.pdf")
        
        return try {
            document.writeTo(FileOutputStream(file))
            document.close()
            file
        } catch (e: Exception) {
            document.close()
            null
        }
    }
}
