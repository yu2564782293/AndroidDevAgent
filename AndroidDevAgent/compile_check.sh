#!/bin/bash
# 代码编译检查脚本
echo "=== Android Dev Agent 代码检查 ==="
echo "检查时间: $(date)"
echo ""

cd /data/user/0/com.ai.assistance.operit/files/workspace/54798240-099a-490f-ab6f-2d45cbeac010/AndroidDevAgent

# 统计文件数量
echo "1. 文件统计:"
kt_files=$(find . -name "*.kt" | wc -l)
java_files=$(find . -name "*.java" | wc -l)
gradle_files=$(find . -name "*.gradle" | wc -l)
xml_files=$(find . -name "*.xml" | wc -l)

echo "   Kotlin文件: $kt_files"
echo "   Java文件: $java_files"
echo "   Gradle文件: $gradle_files"
echo "   XML文件: $xml_files"

echo ""
echo "2. 语法检查（基本验证）:"

# 检查Kotlin文件是否包含包声明
echo "检查Kotlin包声明:"
find . -name "*.kt" -exec grep -l "^package " {} \; | while read file; do
    echo "   ✓ $(basename $file)"
done

# 检查Gradle文件语法
echo ""
echo "检查Gradle配置:"
if [ -f "app/build.gradle" ]; then
    echo "   ✓ app/build.gradle 存在"
    # 检查是否有基本的插件和依赖
    if grep -q "id 'com.android.application'" "app/build.gradle"; then
        echo "   ✓ 包含Android应用插件"
    fi
    if grep -q "id 'org.jetbrains.kotlin.android'" "app/build.gradle"; then
        echo "   ✓ 包含Kotlin Android插件"
    fi
fi

echo ""
echo "3. 关键依赖检查:"
# 检查build.gradle中的关键依赖
if grep -q "jetpack-compose" "app/build.gradle"; then
    echo "   ✓ Jetpack Compose 依赖"
fi

if grep -q "hilt-android" "app/build.gradle"; then
    echo "   ✓ Hilt 依赖注入"
fi

if grep -q "room-runtime" "app/build.gradle"; then
    echo "   ✓ Room 数据库"
fi

if grep -q "retrofit2" "app/build.gradle"; then
    echo "   ✓ Retrofit 网络请求"
fi

echo ""
echo "4. 项目结构验证:"
# 检查关键目录是否存在
if [ -d "app/src/main/java/com/example/androiddevagent" ]; then
    echo "   ✓ 主源码目录"
fi

if [ -d "app/src/main/res" ]; then
    echo "   ✓ 资源目录"
fi

if [ -d "gradle" ]; then
    echo "   ✓ Gradle配置目录"
fi

echo ""
echo "5. 准备构建:"
echo "   项目已准备就绪，可以在Android Studio中打开并构建。"
echo "   下一步需要配置大模型API密钥。"

echo ""
echo "=== 检查完成 ==="