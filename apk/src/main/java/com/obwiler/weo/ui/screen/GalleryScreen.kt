package com.obwiler.weo.ui.screen

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obwiler.weo.photo.PhotoRecord
import com.obwiler.weo.photo.PhotoRepository
import com.obwiler.weo.ui.theme.WeoBlack
import com.obwiler.weo.ui.theme.WeoGreen66
import com.obwiler.weo.ui.theme.WeoGreen99
import com.obwiler.weo.ui.theme.WeoGreenCC
import com.obwiler.weo.ui.theme.WeoGreenFF

@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf<List<PhotoRecord>>(emptyList()) }

    LaunchedEffect(Unit) {
        photos = PhotoRepository.loadAll(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WeoBlack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = "\u76F8\u518C",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = WeoGreenFF,
            )
        }

        if (photos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\u6682\u65E0\u7167\u7247",
                    fontSize = 16.sp,
                    color = WeoGreen66,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(photos) { photo ->
                    var bmp by remember { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(photo.filePath) {
                        bmp = withContext(Dispatchers.IO) {
                            try { BitmapFactory.decodeFile(photo.filePath)?.asImageBitmap() } catch (_: Exception) { null }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(WeoGreen66.copy(alpha = 0.15f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val bitmap = bmp
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(4f / 3f)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Text(
                            text = photo.displayName,
                            fontSize = 9.sp,
                            color = WeoGreen99,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(WeoBlack)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "\u8FD4\u56DE",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WeoGreenFF,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    }
}
