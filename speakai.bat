@echo off
chcp 65001 > nul
title SpeakAI Launcher

:: SpeakAI Launcher Script for Windows
:: Version 1.1.0

:: Определяем цвета (если поддерживается)
if "%1"=="color" goto :color
echo [SpeakAI] Запуск лаунчера...
goto :main

:color
:: Коды цветов для Windows
set RED=91
set GREEN=92
set YELLOW=93
set BLUE=94
set NC=0

:main
:: Функции для вывода цветных сообщений
call :print_message "Инициализация SpeakAI..."

:: Проверяем наличие Java
call :check_java
if errorlevel 1 (
    echo [Ошибка] Java не найдена или версия ниже 17
    pause
    exit /b 1
)

:: Проверяем наличие Maven
call :check_maven
if errorlevel 1 (
    echo [Ошибка] Maven не найден
    pause
    exit /b 1
)

:: Создаем необходимые директории
call :create_directories

:: Проверяем наличие API ключа
call :check_api_key

:: Показываем меню
:menu
cls
echo ========================================
echo          SPEAKAI LAUNCHER v1.1.0
echo ========================================
echo.
echo  1. Запустить приложение (run)
echo  2. Собрать проект (build)
echo  3. Очистить проект (clean)
echo  4. Запустить тесты (test)
echo  5. Показать зависимости
echo  6. Показать справку
echo  7. Выход
echo.
echo ========================================
echo.

set /p choice="Выберите действие (1-7): "

if "%choice%"=="1" goto run_app
if "%choice%"=="2" goto build_app
if "%choice%"=="3" goto clean_app
if "%choice%"=="4" goto test_app
if "%choice%"=="5" goto dependencies
if "%choice%"=="6" goto help
if "%choice%"=="7" goto exit_app
goto menu

:: Запуск приложения
:run_app
cls
echo [SpeakAI] Запуск приложения...
echo ========================================
call mvn javafx:run
if errorlevel 1 (
    echo [Ошибка] Не удалось запустить приложение
) else (
    echo [SpeakAI] Приложение завершило работу
)
pause
goto menu

:: Сборка проекта
:build_app
cls
echo [SpeakAI] Сборка проекта...
call mvn clean package
if errorlevel 1 (
    echo [Ошибка] Ошибка сборки проекта
) else (
    echo [SpeakAI] Сборка завершена успешно!
    for %%i in (target\*.jar) do (
        echo [SpeakAI] JAR файл создан: %%i
        echo Для запуска выполните: java -jar "%%i"
    )
)
pause
goto menu

:: Очистка проекта
:clean_app
cls
echo [SpeakAI] Очистка проекта...
call mvn clean
if errorlevel 1 (
    echo [Ошибка] Ошибка при очистке проекта
) else (
    echo [SpeakAI] Проект очищен
)
pause
goto menu

:: Запуск тестов
:test_app
cls
echo [SpeakAI] Запуск тестов...
call mvn test
if errorlevel 1 (
    echo [Ошибка] Ошибка при выполнении тестов
) else (
    echo [SpeakAI] Тесты выполнены успешно
)
pause
goto menu

:: Показать зависимости
:dependencies
cls
echo [SpeakAI] Зависимости проекта:
echo ========================================
call mvn dependency:tree | findstr /C:"+-" | head -30
echo ========================================
pause
goto menu

:: Справка
:help
cls
echo ========================================
echo          SPEAKAI - СПРАВКА
echo ========================================
echo.
echo Использование:
echo   speakai.bat          - Показать меню
echo   speakai.bat run      - Запустить приложение
echo   speakai.bat build    - Собрать проект
echo   speakai.bat clean    - Очистить проект
echo   speakai.bat test     - Запустить тесты
echo.
echo Требования:
echo   - Java JDK 17 или выше
echo   - Apache Maven 3.6+
echo   - Микрофон (рекомендуется)
echo.
echo Для полноценной работы укажите API ключ OpenAI
echo в файле: src/main/resources/application.properties
echo.
echo ========================================
pause
goto menu

:: Выход
:exit_app
echo [SpeakAI] Выход...
exit /b 0

:: Функция проверки Java
:check_java
where java >nul 2>nul
if errorlevel 1 (
    echo [Ошибка] Java не найдена!
    echo Установите Java JDK 17 или выше
    echo Скачайте с https://adoptium.net
    exit /b 1
)

for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VERSION=%%i"
)
set JAVA_VERSION=%JAVA_VERSION:"=%

echo [SpeakAI] Java найдена: %JAVA_VERSION%

:: Проверка версии Java (упрощенная)
if "%JAVA_VERSION:~0,2%" LSS "17" (
    echo [Ошибка] Требуется Java 17 или выше. Текущая версия: %JAVA_VERSION%
    exit /b 1
)
exit /b 0

:: Функция проверки Maven
:check_maven
where mvn >nul 2>nul
if errorlevel 1 (
    echo [Ошибка] Maven не найден!
    echo Установите Apache Maven
    echo Скачайте с https://maven.apache.org
    exit /b 1
)

for /f "delims=" %%i in ('mvn --version ^| findstr /i "apache maven"') do (
    echo [SpeakAI] %%i
)
exit /b 0

:: Создание директорий
:create_directories
echo [SpeakAI] Проверка структуры директорий...

if not exist "data" mkdir "data" && echo [SpeakAI] Создана директория: data
if not exist "recordings" mkdir "recordings" && echo [SpeakAI] Создана директория: recordings
if not exist "logs" mkdir "logs" && echo [SpeakAI] Создана директория: logs
if not exist "exports" mkdir "exports" && echo [SpeakAI] Создана директория: exports
exit /b 0

:: Проверка API ключа
:check_api_key
if exist "src\main\resources\application.properties" (
    findstr "openai.api.key" "src\main\resources\application.properties" >nul
    if errorlevel 1 (
        echo [Предупреждение] API ключ OpenAI не настроен
    ) else (
        for /f "tokens=2 delims==" %%i in ('findstr "openai.api.key" "src\main\resources\application.properties"') do (
            set "API_KEY=%%i"
        )
        if "%API_KEY%"=="your-api-key-here" (
            echo [Предупреждение] API ключ не указан. Используется демо-режим
        ) else (
            echo [SpeakAI] API ключ OpenAI настроен
        )
    )
) else (
    echo [Предупреждение] Файл application.properties не найден
    echo [Информация] Создайте файл src\main\resources\application.properties
    echo с содержимым: openai.api.key=your-api-key-here
)
exit /b 0

:: Функция вывода сообщений
:print_message
echo [SpeakAI] %~1
exit /b 0