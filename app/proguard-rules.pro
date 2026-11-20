# 保留 ViewModel 无参构造器：Compose 的 viewModel() 通过反射实例化
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Room 实体与 DAO 由 KSP 生成直接引用，这里保留实体字段名以兼容 schema 校验
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
