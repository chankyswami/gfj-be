output "public_ip" {
  value = aws_instance.this.public_ip
}

output "instance_id" {
  description = "The ID of the created EC2 instance"
  value       = aws_instance.this.id
}
