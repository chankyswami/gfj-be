pipeline {
    agent {
        docker {
            image 'maven-terraform-agent:latest'
            args '-v /var/run/docker.sock:/var/run/docker.sock'
        }
    }

    environment {
        EC2_INSTANCE_IP     = '13.203.132.105'
        EC2_INSTANCE_USER   = 'ec2-user'
        DEPLOY_PATH         = '/home/ec2-user'
        AWS_REGION          = 'ap-south-1'
        TF_PLAN_FILE        = 'tfplan'
        SERVICE_NAME        = 'gfj.service'
        START_SCRIPT        = '/home/ec2-user/start.gfj.sh'   // Remote path
    }

    parameters {
        booleanParam(name: 'APPLY_TF', defaultValue: false, description: 'Apply Terraform changes?')
        booleanParam(name: 'DESTROY_TF', defaultValue: false, description: 'Destroy infrastructure instead of running pipeline?')
    }

    stages {
        stage('Terraform Destroy') {
            when { expression { return params.DESTROY_TF == true } }
            steps {
                echo "💣 Destroying infrastructure..."
                withAWS(credentials: 'GEMS-AWS', region: "${env.AWS_REGION}") {
                    sh '''
                        cd terraform-gem/environments/dev
                        terraform init -input=false
                        terraform destroy -auto-approve
                    '''
                }
            }
        }

        stage('Terraform Init & Plan') {
            when { expression { return params.DESTROY_TF == false } }
            steps {
                echo "🌍 Initializing and planning Terraform..."
                withAWS(credentials: 'GEMS-AWS', region: "${env.AWS_REGION}") {
                    sh '''
                        cd terraform-gem/environments/dev
                        terraform init -input=false
                        terraform plan -out=${TF_PLAN_FILE}
                    '''
                }
            }
        }

        stage('Terraform Apply') {
            when { expression { return params.APPLY_TF == true && params.DESTROY_TF == false } }
            steps {
                echo "🚀 Applying Terraform changes..."
                withAWS(credentials: 'GEMS-AWS', region: "${env.AWS_REGION}") {
                    sh '''
                        cd terraform-gem/environments/dev
                        terraform apply -auto-approve ${TF_PLAN_FILE}
                    '''
                }
            }
        }

        stage('Build') {
            when { expression { return params.DESTROY_TF == false } }
            steps {
                sh 'echo "🔧 Checking Maven version..."'
                sh 'mvn -v'

                sh 'echo "🛠️ Building the project..."'
                sh 'mvn clean package -DskipTests=true || exit 1'

                sh 'echo "📦 Listing JARs in target directory..."'
                sh 'ls -lh target/*.jar || echo "❌ No JAR found!"'

                script {
                    def jarFile = sh(script: "ls target/*.jar | grep SNAPSHOT | head -n 1", returnStdout: true).trim()
                    env.JAR_NAME = jarFile.tokenize('/').last()
                    echo "📌 Detected JAR: ${env.JAR_NAME}"
                }
            }
        }

        stage('Prepare Start Script (Managed File + JAR inject)') {
            when { expression { return params.DESTROY_TF == false } }
            steps {
                // Pull start.gfj.sh from Jenkins Managed Files into workspace as start.gfj.sh
                configFileProvider([configFile(fileId: 'start-gfj-script', targetLocation: 'start.gfj.sh')]) {
                    sh '''
                        set -e
                        chmod +x start.gfj.sh
                    '''
                    // Replace the hardcoded jar with the freshly built one
                    // This replaces any "java -jar <something>.jar" line with the new jar name
                    sh """
                        set -e
                        echo "🧩 Injecting new JAR name (${JAR_NAME}) into start.gfj.sh..."
                        sed -i "s|java -jar .*\\.jar|java -jar ${JAR_NAME}|g" start.gfj.sh
                        echo "✅ start.gfj.sh after templating:" && grep -n 'java -jar' start.gfj.sh
                    """
                }
            }
        }

        stage('Deploy') {
            when { expression { return params.DESTROY_TF == false } }
            steps {
                echo "🚚 Deploying to EC2 instance..."
                sshagent(credentials: ['ec2-creds']) {
                    sh """
                        echo "📤 Copying JAR and updated script to EC2..."
                        scp -o StrictHostKeyChecking=no target/${JAR_NAME} ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP}:${DEPLOY_PATH}/
                        scp -o StrictHostKeyChecking=no start.gfj.sh ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP}:${START_SCRIPT}

                        echo "⚙️ Creating/Updating systemd service on EC2..."
                        ssh -o StrictHostKeyChecking=no ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP} '
                            echo "[Unit]
                            Description=GFJ Spring Boot App
                            After=network.target

                            [Service]
                            User=${EC2_INSTANCE_USER}
                            WorkingDirectory=${DEPLOY_PATH}
                            ExecStart=/bin/bash ${START_SCRIPT}
                            Restart=on-failure
                            RestartSec=10
                            StandardOutput=journal
                            StandardError=journal

                            [Install]
                            WantedBy=multi-user.target" | sudo tee /etc/systemd/system/${SERVICE_NAME} > /dev/null
                        '

                        echo "🔄 Restarting service..."
                        ssh -o StrictHostKeyChecking=no ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP} '
                            chmod +x ${START_SCRIPT} &&
                            sudo systemctl daemon-reload &&
                            sudo systemctl enable ${SERVICE_NAME} &&
                            sudo systemctl restart ${SERVICE_NAME} &&
                            sudo systemctl status ${SERVICE_NAME} --no-pager -l
                        '
                    """
                }
            }
        }
    }

    post {
        success { echo '✅ Job finished successfully!' }
        failure { echo '❌ Job failed. Check the logs for more details.' }
    }
}
