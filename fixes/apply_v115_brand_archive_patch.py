from pathlib import Path
import re

root = Path('.')
ui = root/'app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt'
jalali = root/'app/src/main/java/com/nameemrooz/journal/util/JalaliDate.kt'
manifest = root/'app/src/main/AndroidManifest.xml'
build = root/'app/build.gradle.kts'
main_activity = root/'app/src/main/java/com/nameemrooz/journal/MainActivity.kt'

jalali.write_text('''package com.nameemrooz.journal.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object JalaliDate {
    private val week = mapOf(
        Calendar.SATURDAY to "شنبه", Calendar.SUNDAY to "یکشنبه", Calendar.MONDAY to "دوشنبه",
        Calendar.TUESDAY to "سه‌شنبه", Calendar.WEDNESDAY to "چهارشنبه", Calendar.THURSDAY to "پنجشنبه",
        Calendar.FRIDAY to "جمعه"
    )
    private val months = arrayOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
    private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

    fun pretty(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val (jy, jm, jd) = toJalali(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)+1, cal.get(Calendar.DAY_OF_MONTH))
        return persianDigits("${week[cal.get(Calendar.DAY_OF_WEEK)] ?: ""}  $jd ${months[jm-1]} $jy")
    }

    fun archiveStamp(millis: Long): String = "${pretty(millis)}  •  ${time(millis)}"

    fun time(millis: Long): String = persianDigits(SimpleDateFormat("HH:mm", Locale.US).format(Date(millis)))

    fun persianDigits(value: String): String = value.map { c ->
        if (c in '0'..'9') PERSIAN_DIGITS[c - '0'] else c
    }.joinToString("")

    private fun toJalali(gy:Int, gm:Int, gd:Int): Triple<Int,Int,Int> {
        val gdm = intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334)
        var gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365*gy + (gy2+3)/4 - (gy2+99)/100 + (gy2+399)/400 + gd + gdm[gm-1]
        var jy = -1595 + 33*(days/12053); days %= 12053
        jy += 4*(days/1461); days %= 1461
        if (days > 365) { jy += (days-1)/365; days = (days-1)%365 }
        val jm:Int; val jd:Int
        if (days < 186) { jm = 1 + days/31; jd = 1 + days%31 }
        else { jm = 7 + (days-186)/30; jd = 1 + (days-186)%30 }
        return Triple(jy,jm,jd)
    }
}
''', encoding='utf-8')

s = ui.read_text(encoding='utf-8')
anchor = '''private val UiFont = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold)
)
'''
replacement = anchor + '''private val DoranFont = FontFamily(
    Font(R.font.doran_fa_num_regular, FontWeight.Normal),
    Font(R.font.doran_fa_num_bold, FontWeight.SemiBold)
)
'''
if anchor not in s:
    raise SystemExit('UiFont anchor missing')
s = s.replace(anchor, replacement, 1)

s = s.replace('''        persianDigits(SimpleDateFormat("HH:mm", Locale.US).format(Date(now))),
        fontFamily = UiFont,''','''        JalaliDate.time(now),
        fontFamily = DoranFont,''',1)
s = s.replace('''        JalaliDate.pretty(now),
        fontFamily = UiFont,''','''        JalaliDate.pretty(now),
        fontFamily = DoranFont,''',1)

old_heading = '''                Image(
                    painter = painterResource(R.drawable.heading_today_letter),
                    contentDescription = "نامه امروزت",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.End).height(34.dp).fillMaxWidth(0.44f)
                )
'''
new_heading = '''                Text(
                    "نامه امروزت",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right,
                    fontFamily = DoranFont,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Olive
                )
'''
if old_heading not in s:
    raise SystemExit('today letter image heading anchor missing')
s = s.replace(old_heading, new_heading, 1)

start = s.index('@Composable\nprivate fun LetterEditorCard')
end = s.index('@Composable\nprivate fun SoftListeningDot', start)
card = s[start:end]
card = card.replace('fontFamily = UiFont', 'fontFamily = DoranFont')
card = card.replace('fontFamily = TranscriptFont', 'fontFamily = DoranFont')
s = s[:start] + card + s[end:]

old_archive = '''        Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 15.dp)) {
            Text(entry.title, Modifier.fillMaxWidth(), textAlign = TextAlign.Right, fontFamily = UiFont, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Olive)
            Spacer(Modifier.height(4.dp))
            Text(entry.text.take(110), Modifier.fillMaxWidth(), textAlign = TextAlign.Right, maxLines = 2, fontFamily = TranscriptFont, fontSize = 16.sp, lineHeight = 27.sp, color = Ink.copy(alpha = 0.72f))
            Spacer(Modifier.height(8.dp))
            Text("${JalaliDate.pretty(entry.createdAt)}  •  ${formatTime(entry.createdAt)}", fontFamily = UiFont, fontSize = 13.sp, color = Muted)
        }
'''
new_archive = '''        Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 15.dp)) {
            Text(
                JalaliDate.archiveStamp(entry.createdAt),
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right,
                fontFamily = DoranFont,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Olive
            )
            Spacer(Modifier.height(8.dp))
            Text(entry.text.take(110), Modifier.fillMaxWidth(), textAlign = TextAlign.Right, maxLines = 2, fontFamily = DoranFont, fontSize = 17.sp, lineHeight = 29.sp, color = Ink.copy(alpha = 0.76f))
        }
'''
if old_archive not in s:
    raise SystemExit('archive card anchor missing')
s = s.replace(old_archive, new_archive, 1)

s = s.replace('''        Text(JalaliDate.pretty(entry.createdAt), Modifier.fillMaxWidth(), textAlign = TextAlign.Right, fontFamily = UiFont, color = Muted, fontSize = 14.sp)''','''        Text(JalaliDate.archiveStamp(entry.createdAt), Modifier.fillMaxWidth(), textAlign = TextAlign.Right, fontFamily = DoranFont, color = Muted, fontSize = 15.sp)''',1)
detail_start = s.index('@Composable\nprivate fun Detail')
detail_end = s.index('@Composable\nprivate fun Settings', detail_start)
detail = s[detail_start:detail_end]
detail = detail.replace('fontFamily = TranscriptFont', 'fontFamily = DoranFont')
detail = detail.replace('textStyle = TextStyle(fontFamily = UiFont,', 'textStyle = TextStyle(fontFamily = DoranFont,')
s = s[:detail_start] + detail + s[detail_end:]

cover_start = s.index('@Composable\nfun EmroozPrivacyCover')
cover_end = s.index('private fun formatTime', cover_start)
cover = s[cover_start:cover_end]
if 'R.drawable.emrooz_logo' not in cover:
    raise SystemExit('splash logo missing')
if cover.count('Image(') != 1:
    raise SystemExit(f'splash must contain exactly one Image; got {cover.count("Image(")}')
cover = cover.replace('Text("هر چیزی که بگی مهمه", fontFamily = UiFont,', 'Text("هر چیزی که بگی مهمه", fontFamily = DoranFont,')
s = s[:cover_start] + cover + s[cover_end:]

s = re.sub(r'\nprivate fun formatTime\(epochMillis: Long\): String = .*?\nprivate fun persianDigits\(value: String\): String = .*?\n?$', '\n', s, flags=re.S)
s = s.replace('import java.text.SimpleDateFormat\n', '')
s = s.replace('import java.util.Date\n', '')
s = s.replace('import java.util.Locale\n', '')
ui.write_text(s, encoding='utf-8')

m = main_activity.read_text(encoding='utf-8')
m, n = re.subn(r'val remaining = [0-9_]+L - elapsed', 'val remaining = 3_000L - elapsed', m, count=1)
if n != 1:
    raise SystemExit('3-second splash anchor missing')
main_activity.write_text(m, encoding='utf-8')

ms = manifest.read_text(encoding='utf-8')
ms = ms.replace('android:icon="@mipmap/ic_launcher"', 'android:icon="@drawable/app_icon_exact"')
ms = ms.replace('android:roundIcon="@mipmap/ic_launcher_round"', 'android:roundIcon="@drawable/app_icon_exact"')
manifest.write_text(ms, encoding='utf-8')

b = build.read_text(encoding='utf-8')
b, n0 = re.subn(r'applicationId\s*=\s*"[^"]+"', 'applicationId = "com.nameemrooz.journal.v115final"', b, count=1)
b, n1 = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 18', b, count=1)
b, n2 = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.1.5"', b, count=1)
if (n0,n1,n2) != (1,1,1):
    raise SystemExit(f'build config patch failed {(n0,n1,n2)}')
build.write_text(b, encoding='utf-8')
