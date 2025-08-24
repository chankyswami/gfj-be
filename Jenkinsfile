pipeline {
    agent {
        docker {
            image 'maven-terraform-agent:latest'
            args '-v /var/run/docker.sock:/var/run/docker.sock'
        }
    }

    environment {
        EC2_INSTANCE_IP     = '13.202.224.13'
        EC2_INSTANCE_USER   = 'ec2-user'
        DEPLOY_PATH         = '/home/ec2-user'
        AWS_REGION          = 'ap-south-1'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Terraform Init & Plan') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'GEMS-AWS',
                        usernameVariable: 'AWS_ACCESS_KEY_ID',
                        passwordVariable: 'AWS_SECRET_ACCESS_KEY'
                    )
                ]) {
                    sh '''
                        echo "🌍 Initializing Terraform..."
                        export AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID
                        export AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY
                        export AWS_DEFAULT_REGION=ap-south-1

                        terraform init
                        terraform plan -out=tfplan
                    '''
                }
            }
        }

        stage('Terraform Apply') {
            when {
                expression { return params.APPLY_TF == true }
            }
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'GEMS-AWS',
                        usernameVariable: 'AWS_ACCESS_KEY_ID',
                        passwordVariable: 'AWS_SECRET_ACCESS_KEY'
                    )
                ]) {
                    sh '''
                        echo "🚀 Applying Terraform changes..."
                        export AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID
                        export AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY
                        export AWS_DEFAULT_REGION=ap-south-1

                        terraform apply -auto-approve tfplan
                    '''
                }
            }
        }

        stage('Build') {
            steps {
                sh 'echo "🔧 Checking Maven version..."'
                sh 'mvn -v'

                sh 'echo "🛠️ Building the project..."'
                sh 'mvn clean install -DskipTests=true || exit 1'

                sh 'echo "📦 Listing JARs in target directory..."'
                sh 'ls -lh target/*.jar || echo "❌ No JAR found!"'

                script {
                    def jarFile = sh(script: "ls target/*.jar | grep SNAPSHOT | head -n 1", returnStdout: true).trim()
                    env.JAR_NAME = jarFile.tokenize('/').last()
                    echo "📌 Detected JAR: ${env.JAR_NAME}"
                }
            }
        }

        stage('Deploy') {
            steps {
                configFileProvider([configFile(fileId: 'start-gfj-script', variable: 'START_SCRIPT_PATH')]) {
                    sshagent(credentials: ['ec2-creds']) {
                        sh """
                            echo "🚀 Copying application files to EC2 instance..."

                            scp -o StrictHostKeyChecking=no target/${JAR_NAME} ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP}:${DEPLOY_PATH}/

                            scp -o StrictHostKeyChecking=no ${START_SCRIPT_PATH} ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP}:${DEPLOY_PATH}/start-gfj.sh

                            echo "🔄 Connecting to EC2 instance and restarting application..."

                            ssh -o StrictHostKeyChecking=no ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP} '
                                echo "🛑 Stopping any existing application process..." &&
                                sudo pkill -f "${JAR_NAME}" || true &&
                                sudo chmod +x ${DEPLOY_PATH}/start-gfj.sh &&
                                echo "▶️ Starting the new application using the start script as root..." &&
                                sudo nohup bash ${DEPLOY_PATH}/start-gfj.sh > /var/log/gfj.log 2>&1 &
                            '
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            echo '✅ Deployment successful!'
        }
        failure {
            echo '❌ Deployment failed. Check the logs for more details.'
        }
    }
}






// pipeline {
//     agent any

//     environment {
//         EC2_INSTANCE_IP = '13.202.224.13'
//         EC2_INSTANCE_USER = 'ec2-user'
//         DEPLOY_PATH = '/home/ec2-user'
//     }

//     stages {
//         stage('Checkout') {
//             steps {
//                 checkout scm
//             }
//         }

//         stage('Build') {
//             steps {
//                 sh 'echo "🔧 Checking Maven version..."'
//                 sh 'mvn -v'

//                 sh 'echo "🛠️ Building the project..."'
//                 sh 'mvn clean install -DskipTests=true || exit 1'

//                 sh 'echo "📦 Listing JARs in target directory..."'
//                 sh 'ls -lh target/*.jar || echo "❌ No JAR found!"'

//                 script {
//                     def jarFile = sh(script: "ls target/*.jar | grep SNAPSHOT | head -n 1", returnStdout: true).trim()
//                     env.JAR_NAME = jarFile.tokenize('/').last()
//                     echo "📌 Detected JAR: ${env.JAR_NAME}"
//                 }
//             }
//         }

//         stage('Deploy') {
//             steps {
//                 configFileProvider([configFile(fileId: 'start-gfj-script', variable: 'START_SCRIPT_PATH')]) {
//                     sshagent(credentials: ['ec2-creds']) {
//                         sh """
//                             echo "🚀 Copying application files to EC2 instance..."

//                             scp -o StrictHostKeyChecking=no target/${JAR_NAME} ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP}:${DEPLOY_PATH}/

//                             scp -o StrictHostKeyChecking=no ${START_SCRIPT_PATH} ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP}:${DEPLOY_PATH}/start-gfj.sh

//                             echo "🔄 Connecting to EC2 instance and restarting application..."

//                             ssh -o StrictHostKeyChecking=no ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP} '
//                                 echo "🛑 Stopping any existing application process..." &&
//                                 sudo pkill -f "${JAR_NAME}" || true &&
//                                 sudo chmod +x ${DEPLOY_PATH}/start-gfj.sh &&
//                                 echo "▶️ Starting the new application using the start script as root..." &&
//                                 sudo nohup bash ${DEPLOY_PATH}/start-gfj.sh > /var/log/gfj.log 2>&1 &
//                             '
//                         """
//                     }
//                 }
//             }
//         }
//     }

//     post {
//         success {
//             echo '✅ Deployment successful!'
//         }
//         failure {
//             echo '❌ Deployment failed. Check the logs for more details.'
//         }
//     }
// }
