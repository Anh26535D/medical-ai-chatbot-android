package edu.hust.medicalaichatbot.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.hust.medicalaichatbot.R
import edu.hust.medicalaichatbot.ui.theme.PrimaryBlue
import edu.hust.medicalaichatbot.ui.theme.SurfaceGray
import edu.hust.medicalaichatbot.ui.theme.TextGray

@Composable
fun CommonTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    onProfileClick: () -> Unit
) {
    Surface(
        shadowElevation = 2.dp,
        color = Color.White,
        modifier = Modifier.statusBarsPadding()
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = PrimaryBlue
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title ?: stringResource(R.string.app_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                    Text(
                        text = subtitle ?: "Trợ lý sức khỏe AI",
                        fontSize = 12.sp,
                        color = TextGray
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onProfileClick() },
                shape = CircleShape,
                color = SurfaceGray
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Tài khoản",
                        tint = PrimaryBlue
                    )
                }
            }
        }
    }
}
