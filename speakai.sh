#!/bin/bash

# SpeakAI Launcher Script for Linux/macOS
# Version 1.1.0

# Определяем цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Функция для вывода сообщений с цветом
print_message() {
    echo -e "${GREEN}[SpeakAI]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[Предупреждение]${NC} $1"
}

print_error() {
    echo -e "${RED}[Ошибка]${NC} $1"
}

print_info() {
    echo -e "${BLUE}[Информация]${NC} $1"
}

# Проверяем наличие Maven
check_maven() {
    if ! command -v mvn &> /dev/null; then
        print_error "Maven не найден!"
        echo "Установите Maven:"
        echo "  Ubuntu/Debian: sudo apt-get install maven"
        echo "  macOS: brew install maven"
        echo "  или скачайте с https://maven.apache.org"
        exit 1
    fi
    print_message "Maven найден: $(mvn --version | head -1)"
}

# Проверяем наличие Java
check_java() {
    if ! command -v java &> /dev/null; then
        print_error "Java не найдена!"
        echo "Установите Java JDK 17 или выше:"
        echo "  Ubuntu/Debian: sudo apt-get install openjdk-17-jdk"
        echo "  macOS: brew install openjdk@17"
        echo "  или скачайте с https://adoptium.net"
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2)
    print_message "Java найдена: $JAVA_VERSION"

    # Проверяем версию Java
    MAJOR_VERSION=$(echo $JAVA_VERSION | cut -d'.' -f1)
    if [ "$MAJOR_VERSION" -lt 17 ]; then
        print_error "Требуется Java 17 или выше. Текущая версия: $JAVA_VERSION"
        exit 1
    fi
}

# Создаем необходимые директории
create_directories() {
    print_info "Проверка структуры директорий..."

    DIRECTORIES=("data" "recordings" "logs" "exports")

    for dir in "${DIRECTORIES[@]}"; do
        if [ ! -d "$dir" ]; then
            mkdir -p "$dir"
            print_message "Создана директория: $dir"
        fi
    done
}

# Проверяем наличие API ключа OpenAI
check_api_key() {
    if [ -f "src/main/resources/application.properties" ]; then
        API_KEY=$(grep "openai.api.key" src/main/resources/application.properties | cut -d'=' -f2)
        if [ -z "$API_KEY" ] || [ "$API_KEY" = "your-api-key-here" ]; then
            print_warning "API ключ OpenAI не настроен!"
            echo "Для полноценной работы укажите ваш API ключ в файле:"
            echo "  src/main/resources/application.properties"
            echo ""
            echo "Пока будет использоваться демонстрационный режим."
        else
            print_message "API ключ OpenAI настроен"
        fi
    else
        print_warning "Файл application.properties не найден"
        print_info "Создайте файл src/main/resources/application.properties с содержимым:"
        echo "openai.api.key=your-api-key-here"
    fi
}

# Показываем меню помощи
show_help() {
    echo "Использование: $0 [команда]"
    echo ""
    echo "Команды:"
    echo "  run         - Запустить приложение (по умолчанию)"
    echo "  build       - Собрать проект (mvn clean package)"
    echo "  clean       - Очистить проект (mvn clean)"
    echo "  test        - Запустить тесты"
    echo "  dependencies - Показать зависимости проекта"
    echo "  help        - Показать эту справку"
    echo ""
    echo "Примеры:"
    echo "  $0 run      - Запустить SpeakAI"
    echo "  $0 build    - Собрать JAR файл"
    echo "  $0 clean    - Очистить проект"
}

# Основная функция запуска
run_application() {
    print_message "Запуск SpeakAI..."

    # Проверяем зависимости
    check_java
    check_maven

    # Создаем директории
    create_directories

    # Проверяем API ключ
    check_api_key

    print_info "Запуск JavaFX приложения..."
    echo "========================================"

    # Запускаем приложение через Maven
    mvn javafx:run

    EXIT_CODE=$?

    if [ $EXIT_CODE -eq 0 ]; then
        print_message "Приложение завершило работу корректно"
    else
        print_error "Приложение завершило работу с ошибкой (код: $EXIT_CODE)"
    fi
}

# Сборка проекта
build_project() {
    print_message "Сборка проекта SpeakAI..."

    check_java
    check_maven
    create_directories

    print_info "Запуск сборки..."
    mvn clean package

    if [ $? -eq 0 ]; then
        print_message "Сборка завершена успешно!"
        JAR_FILE=$(ls target/*.jar 2>/dev/null | head -1)
        if [ -f "$JAR_FILE" ]; then
            print_message "JAR файл создан: $JAR_FILE"
            print_info "Для запуска выполните: java -jar \"$JAR_FILE\""
        fi
    else
        print_error "Ошибка сборки проекта"
        exit 1
    fi
}

# Очистка проекта
clean_project() {
    print_message "Очистка проекта..."
    mvn clean
    if [ $? -eq 0 ]; then
        print_message "Проект очищен"
    else
        print_error "Ошибка при очистке проекта"
    fi
}

# Запуск тестов
run_tests() {
    print_message "Запуск тестов..."
    mvn test
}

# Показать зависимости
show_dependencies() {
    print_message "Зависимости проекта:"
    mvn dependency:tree | head -50
}

# Обработка аргументов
case "$1" in
    "run"|"")
        run_application
        ;;
    "build")
        build_project
        ;;
    "clean")
        clean_project
        ;;
    "test")
        run_tests
        ;;
    "dependencies")
        show_dependencies
        ;;
    "help"|"-h"|"--help")
        show_help
        ;;
    *)
        print_error "Неизвестная команда: $1"
        show_help
        exit 1
        ;;
esac