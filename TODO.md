# Kradus Media — qabul mezonlari

> Native `todo` amali loyihada mavjud emas; shu sabab outcome va to‘liq mezonlar shu faylda yuritiladi.

## Avvalgi talablar

- [x] **O‘zbekcha Kradus mobil interfeysi va brending:** “shu logo dan foydalanib menga juddalarda ham takomillashib ketgan kradus media (MP3) player ni yaratasan”; mobil ekranlar va sensorli boshqaruvlar uchun moslashtirilgan o‘zbekcha interfeys; Kradus logotipi va mavjud brend uslubiga mos yuqori sifatli vizual tajriba.
- [x] **MP3 qo‘shish, papkalar va tahrirlash:** foydalanuvchi MP3 musiqani qurilmadan yoki ko‘rsatgan manbadan qo‘sha oladi; musiqalar papkalar bo‘yicha yaratiladi, ko‘riladi va tartibli saqlanadi; foydalanuvchi trek ma’lumotlarini tahrirlaydi va trekni boshqa papkaga joylaydi/ko‘chiradi.
- [x] **Kutubxona, qidiruv va tezkor to‘plamlar:** qo‘shiqlar nomi, ijrochisi yoki papkasi bo‘yicha ko‘riladi va qidiriladi; sevimlilar va ijro navbati boshqariladi; papkalar va kutubxona holati ilova qayta ochilganda saqlanadi.
- [x] **Asosiy ijro boshqaruvi:** qo‘shiqni ijro etish, pauza, oldingi va keyingi trekka o‘tish; joriy ijro vaqtini ko‘rsatish, trek bo‘ylab surish va ovoz balandligini boshqarish; takrorlash va tasodifiy ijro rejimlari ishlaydi.
- [x] **To‘liq ekranli pleyer:** foydalanuvchi joriy trek ma’lumoti va cover tasvirini ko‘radigan, mobilga mos to‘liq ekranli pleyerni ochadi va undan ijroni boshqaradi.
- [x] **Native brend aktivlari va barqaror ishga tushish:** Kradus logotipi asosidagi kvadrat launcher icon, splash, favicon va Android adaptive icon’lar mavjud; Expo portlari 8081/3000 va boshlang‘ich loyiha identifikatorlari saqlangan.

## Yangi talablar

- [ ] **Qotmaydigan, optimizatsiya qilingan ilova:** “toʻliq optimizatsita qilasan qotmasin”; uzun treklar va sevimlilar ro‘yxati ekran tashqarisidagi qatorlarni birvarakayiga render qilmaydi; audio progress yangilanishi 300 ms da emas, ko‘pi bilan sekundiga bir marta UI’ga chiqadi; ijro navbati ID’lardan samarali ko‘riladi; tez-tez o‘zgaradigan sozlamalar diskka alohida-alohida yozilmaydi.
- [ ] **Ilovaga mos klassik dizayn:** “dezaynni ham ilovaga mos klassik dezayn qil ai dek boʻlmasin”; neon glow, rangli orbit/halqa, sun’iy gradient va doimiy puls effektlari olib tashlanadi; klassik hi-fi pleerga mos grafit/neytral ranglar, mo‘tadil Kradus-ko‘k aksent, sodda kvadrat album-art, odatiy native boshqaruvlar va ravshan matn ishlatiladi.
- [ ] **Foydalanuvchi kutubxonasi va telefon xotirasini to‘g‘ri saqlash:** “userni xotirasi bilan toʻgʻri ishlasin”; mavjud `v1` AsyncStorage kutubxonasi yo‘qotilmasdan versiyalangan yangi formatga ko‘chadi; keyingi yozuvlar debounce va serial navbat bilan bajariladi; trek/papka/sevimli/navbat/sozlamalar ilova qayta ochilganda tiklanadi; tanlangan MP3 private app document katalogiga noyob nom bilan nusxalanadi; trek o‘chirilganda ilova nusxasi ham tozalanadi; mahalliy audio serverga yuborilmaydi.
- [ ] **URL treklar uchun doimiy papka:** “url orqali qoʻshilgan musiqalari malum bir folder yaratilib saqlanib borilishi kerak”; ilk URL importida yagona “Internetdan qo‘shilganlar” papkasi yaratiladi; barcha keyingi URL treklar shu papkaga yoziladi; papka va URL/metadata restartdan keyin saqlanadi; trekni keyinchalik foydalanuvchi boshqa papkaga ko‘chira oladi; URL’lar xotirani kutilmaganda to‘ldirish uchun avtomatik yuklab olinmaydi va oqimli ijro internet talab qiladi.
- [ ] **Ruxsatlarga ehtiyotkor yondashuv:** “permession ga ham ehtiyot boʻl”; faqat system document picker orqali foydalanuvchi tanlagan MP3 olinadi; ilova umumiy xotira, barcha media, mikrofon, kamera yoki joylashuv ruxsatlarini so‘ramaydi; RECORD_AUDIO o‘chirilgan; Android media bildirishnomasi faqat playback foydalanuvchi tomonidan boshlanganida so‘raladi va rad etilsa ham ijro ishlaydi.

**Native sinov eslatmasi:** oldingi versiyada jismoniy Expo Go qurilmasida fayl tanlagichi, fon ijrosi va lock-screen boshqaruvlari sinovdan o‘tkazilmagan; bu cheklov ushbu sessiya yakunida qayta qayd etiladi.
