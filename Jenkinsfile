pipeline {
  agent any

  tools {
    // must match your Global Tool Config names exactly
    jdk  'Java 21'
    maven 'Maven 3.8.1'
  }

  stages {
    stage('Checkout') {
      steps {
        git url: 'https://github.com/Sai-Roopesh/pipeline-test.git',
            branch: 'main',
            credentialsId: 'git-cred-test'
      }
    }

    stage('Compile & Test') {
      steps {
        sh 'mvn clean verify'
      }
    }

    stage('Package') {
      steps {
        sh 'mvn package -DskipTests'
      }
      post {
        success {
          archiveArtifacts artifacts: 'target/my-app-1.0.0.jar', fingerprint: true
        }
      }
    }

    stage('Smoke Test') {
      steps {
        // launch the jar in background, wait 3s, then kill it
        sh '''
          java -jar target/my-app-1.0.0.jar &
          APP_PID=$!
          sleep 3
          kill $APP_PID
        '''
      }
    }

    // ◉ if/when you add Sonar, Nexus or Docker steps you can slot them in here:
    //
    // stage('SonarQube Analysis') { … }
    // stage('Publish to Nexus')   { … }
    // stage('Build & Push Docker') { … }
  }
}
