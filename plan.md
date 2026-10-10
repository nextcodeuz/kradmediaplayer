# Kradus Media — amalga oshirish rejasi

## Mahsulot va texnik yondashuv

Mavjud Expo Router / React Native / TypeScript / NativeWind mobil loyihasi saqlanadi. Yangi talab bo‘yicha eski neon/AI-ko‘rinishidagi bezaklar o‘rniga an’anaviy hi-fi musiqa pleeriga xos, sodda va o‘qilishi qulay vizual til qo‘llanadi. Boshqaruvlar darhol javob beradi; uzun trek ro‘yxatlari virtualizatsiya qilinadi, audio holati sekundiga ko‘pi bilan bir marta UI’ga uzatiladi va kutubxona yozuvlari tinch davrda bir marta saqlanadi.

Trek nomi, ijrochi, URL manzili, papka, navbat va sozlamalar AsyncStorage’da versionlangan schema bilan saqlanadi. Avvalgi `kradus.media.library.v1` ma’lumotlari yo‘qotilmay `v2` formatiga ko‘chiriladi. Tez-tez o‘zgaradigan volume/progress UI qiymatlari har bir siljishda butun kutubxonani yozmaydi: o‘zgarishlar qisqa debounce bilan jamlanadi, disk yozuvlari navbatma-navbat bajariladi, ilova fon rejimiga o‘tishda oxirgi holat flush qilinadi.

Qurilmadan tanlangan MP3 system document picker orqali so‘raladi — ilovaga butun media kutubxonasi yoki umumiy xotiraga keng ruxsat berilmaydi. Fayl ilovaning private document katalogiga noyob nom bilan nusxalanadi; trek o‘chirilganda aynan shu ilova yaratgan nusxa ham tozalanadi. Mikrofon/recording ruxsati o‘chirib qo‘yiladi. Android media bildirishnomasi faqat foydalanuvchi ijroni boshlaganda so‘ralishi mumkin; rad etilgan taqdirda audio ijrosi davom etadi. Video, PiP, foto, joylashuv yoki audio yozish imkoniyati qo‘shilmaydi.

URL orqali qo‘shilgan har bir trek yangi yoki oldindan mavjud bo‘lgan yagona **“Internetdan qo‘shilganlar”** papkasiga avtomatik tushadi; papka va trek metama’lumoti qurilmada saqlanadi va qayta ochilganda tiklanadi. Trek bevosita manbadan oqimli ijro qilinadi: MP3 fayl yashirincha yuklab olinmaydi va telefon xotirasini kutilmaganda egallamaydi. Foydalanuvchi trekni keyin boshqa papkaga ko‘chirishi mumkin. Mahalliy audio hech qachon serverga yuborilmaydi.

## Klassik dizayn tizimi

- **Design Movement:** klassik portable hi-fi apparatlari va album liner-notes estetikasi; zamonaviy mobil tartib, ammo effekt va bezaksiz.
- **Core Principles:** sodda; musiqani birinchi o‘ringa qo‘yadi; o‘qilishi aniq; foydalanuvchi ma’lumoti va qurilma resursini hurmat qiladi.
- **Color Philosophy:** grafit va issiq oq matn ko‘zga tinch; brendning ko‘k rangi faqat faol ijro va tanlangan holatlarni aniq belgilaydi; cyan/purple neon, yaltiroq halqa va gradientlar ishlatilmaydi.
- **Layout Paradigm:** bir ustunli native media-library oqimi; ixcham sarlavha, oddiy album-art kvadratlari, chiziqli progress va doimiy mini-player; uzun ro‘yxatlar `FlatList` bilan ko‘rinadigan qismigagina chiziladi.
- **Signature Elements:** K logotipli sodda brand header; kvadrat album-art; aniq progress chizig‘i va fizik pleerga o‘xshash play/pause boshqaruvi.
- **Interaction Philosophy:** odatiy mobil gesture va bir bosishli controls; fayl tanlashda faqat user tanlagan MP3 olinadi; URL tracklar uchun internet zarurligi aniq aytiladi.
- **Animation:** keraksiz doimiy puls/yorug‘lik yo‘q; faqat qisqa route/modal transition va tugma bosilganidagi yengil feedback.
- **Typography System:** operatsion tizim sans-serif; sarlavha 22–28 px, trek nomi 14–16 px, yordamchi matn kamida 11–12 px; kichik uppercase texnik yorliqlar kamaytiriladi.
- **Brand Essence:** “Kradus Media — shaxsiy MP3 kutubxonangiz uchun oddiy, ishonchli hi-fi pleer.” Shaxsiyati: **sokin, aniq, ishonchli**.
- **Brand Voice:** o‘zbekcha va amaliy; xotira, internet yoki ruxsatlar haqida yashirmaydi. Misollar: “Havola saqlandi. Ijro uchun internet kerak.” / “Fayl faqat ushbu ilovaning xotirasida turadi.”
- **Wordmark & Logo:** yuborilgan KRADUS markasi mavjud assetlarda qoladi; logotip atrofida neon/glow ishlatilmaydi, header va ilova belgisi yagona brend manbasi bo‘ladi.
- **Signature Brand Color:** Kradus Blue — `#386AA3`; fon, divider va matn ranglari grafit, sovuq kulrang va yumshoq oqdan iborat.

## Ekranlar va loyiha tuzilmasi

`app/` ichida Tinglash, Kutubxona, Papkalar, Sevimlilar va to‘liq ekranli player saqlanadi. `components/music/` qayta ishlatiladigan, sodda album-art, trek qatori, progress, mini-player va modal komponentlariga ega bo‘ladi. `lib/music/types.ts` trek/papka turlarini, `storage.ts` schema migratsiyasi va private file lifecycle’ni, `MusicProvider.tsx` Expo Audio ijrosi hamda throttled state/persistence’ni boshqaradi. `assets/images/`da foydalanuvchi bergan Kradus brendingi ishlatiladi. Managed Metro/API portlari 8081/3000, app id va deep-link sxemasi saqlanadi.
