package com.accesspath.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.accesspath.domain.*

@Composable fun CampusCanvas(s: AppSnapshot, route: RouteResult.Found?) {
    var zoom by remember(s.selectedFloor) { mutableFloatStateOf(1f) }
    var pan by remember(s.selectedFloor) { mutableStateOf(Offset.Zero) }
    val floorNodes=remember(s.map,s.selectedFloor){s.map.nodes.filter{it.floorId==s.selectedFloor}}
    val nodes=s.map.nodes.associateBy{it.id}
    val excluded=remember(s){Routing.exclusions(s)}
    val active=route?.edges?.map{it.id}?.toSet().orEmpty()
    val text=remember{Paint(Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.rgb(31,58,49);textSize=25f;typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD)}}
    Column {
        Canvas(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFEAF1EC))
            .semantics{contentDescription="Floor map ${s.selectedFloor}. ${floorNodes.size} landmarks. Use the route step list for accessible directions."}
            .pointerInput(s.selectedFloor){detectTransformGestures{_,delta,scale,_->zoom=(zoom*scale).coerceIn(1f,4f);pan+=delta}}) {
            val maxX=(floorNodes.maxOfOrNull{it.x}?:100.0).coerceAtLeast(100.0).toFloat()+25f
            val maxY=(floorNodes.maxOfOrNull{it.y}?:100.0).coerceAtLeast(100.0).toFloat()+20f
            fun point(n:Node)=Offset(25f+n.x.toFloat()/maxX*(size.width-50f),35f+n.y.toFloat()/maxY*(size.height-70f))
            for(i in 0..12) drawLine(Color(0xFFDCE6DF),Offset(i*size.width/12,0f),Offset(i*size.width/12,size.height),1f)
            for(i in 0..10) drawLine(Color(0xFFDCE6DF),Offset(0f,i*size.height/10),Offset(size.width,i*size.height/10),1f)
            withTransform({translate(pan.x,pan.y);scale(zoom,zoom,center)}) {
                s.map.edges.filter{nodes[it.from]?.floorId==s.selectedFloor && nodes[it.to]?.floorId==s.selectedFloor}
                    .sortedBy{if(it.id in active)1 else 0}.forEach{e->
                        val color=when{e.id in excluded->Color(0xFFB64235);e.id in active->Color(0xFF126D51);else->Color(0xFFAFBDB2)}
                        drawLine(color,point(nodes.getValue(e.from)),point(nodes.getValue(e.to)),if(e.id in active)12f else 7f,StrokeCap.Round,
                            pathEffect=if(e.id in excluded)PathEffect.dashPathEffect(floatArrayOf(12f,8f)) else null)
                    }
                floorNodes.forEach{n->val pt=point(n)
                    drawCircle(Color.White,13f,pt)
                    drawCircle(when(n.id){s.current->Color(0xFF125B50);s.destination->Color(0xFF8D5B14);else->Color(0xFF779583)},9f,pt)
                    text.textSize=22f
                    drawContext.canvas.nativeCanvas.drawText(n.name.take(22),pt.x+14f,pt.y-10f,text)
                }
            }
        }
        TextButton(onClick={zoom=1f;pan=Offset.Zero}){Text("Reset map view")}
    }
}
